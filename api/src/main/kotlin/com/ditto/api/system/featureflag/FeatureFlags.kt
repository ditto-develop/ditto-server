package com.ditto.api.system.featureflag

import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 서버 기능 플래그 읽기. 인스턴스마다 [TTL](1분) 동안 캐시한다 — 메시지 전송·매분 스케줄러처럼 자주 묻는 경로가
 * 매번 DB 를 읽지 않게 한다. 대가로 어드민이 바꾼 값이 **다른 인스턴스에는 최대 1분 늦게** 보인다.
 * 바꾼 인스턴스는 [evict]로 곧바로 반영한다.
 *
 * 플래그 전체를 한 번에 읽어 한 덩어리로 캐시한다. 행이 몇 개뿐이라 키별로 나눌 이유가 없고,
 * 한 덩어리여야 같은 순간에 읽은 값끼리 어긋나지 않는다.
 *
 * @param nanoTicker 단조 시계(나노초). 테스트가 캐시 만료를 흉내 내려고 바꿔 끼운다.
 */
@Component
class FeatureFlags(
    private val featureFlagRepository: FeatureFlagRepository,
    private val nanoTicker: () -> Long,
) {
    @Autowired
    constructor(featureFlagRepository: FeatureFlagRepository) : this(featureFlagRepository, System::nanoTime)

    @Volatile
    private var cached: Snapshot? = null

    fun isEnabled(key: FeatureFlagKey): Boolean = snapshot().enabledKeys.contains(key)

    /**
     * 이름(문자열)으로 묻는 켜짐 여부. 요청한 순서대로 담고, 한 스냅샷에서 읽어 값끼리 어긋나지 않는다.
     * [FeatureFlagKey]에 없는 이름은 false — FE 가 BE 보다 먼저 새 키를 확인하는 코드를 배포할 수 있게 한다.
     * 단 공개를 마쳐 지운 키([RETIRED_ALWAYS_ON])는 true 다.
     */
    fun statesOf(rawKeys: Collection<String>): Map<String, Boolean> {
        val enabledNames = snapshot().enabledKeys.mapTo(HashSet()) { it.name }
        return rawKeys.associateWith { it in enabledNames || it in RETIRED_ALWAYS_ON }
    }

    /** 이 인스턴스의 캐시를 버린다. 다음 조회가 DB 를 다시 읽는다. */
    fun evict() {
        cached = null
    }

    private fun snapshot(): Snapshot {
        val now = nanoTicker()
        cached?.takeIf { now - it.loadedAtNanos < TTL.toNanos() }?.let { return it }

        val enabledKeys = featureFlagRepository.findAllByKeyIn(FeatureFlagKey.entries).filter { it.enabled }.map { it.key }.toSet()
        return Snapshot(enabledKeys, now).also { cached = it }
    }

    private class Snapshot(val enabledKeys: Set<FeatureFlagKey>, val loadedAtNanos: Long)

    companion object {
        val TTL: Duration = Duration.ofMinutes(1)

        /**
         * 켜서 공개를 마치고 enum 에서 지운 키. 조회 API 는 이 키에 계속 true 로 답한다 — FE 는 키를 지운 빌드를 먼저
         * 내지만, 그 전에 열린 탭·웹뷰는 옛 빌드로 이 키를 묻는다. false 로 답하면 그 화면에서 공개된 기능이 사라진다.
         */
        val RETIRED_ALWAYS_ON: Set<String> = setOf("QUIZ_CHAT", "RECORDS")
    }
}
