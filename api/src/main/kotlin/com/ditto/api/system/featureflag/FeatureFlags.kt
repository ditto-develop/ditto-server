package com.ditto.api.system.featureflag

import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * 서버 기능 플래그 읽기. 인스턴스마다 [TTL](1분) 동안 캐시한다 — 메시지 전송·매분 스케줄러처럼 자주 묻는 경로가
 * 매번 DB 를 읽지 않게 한다. 대가로 어드민이 바꾼 값이 **다른 인스턴스에는 최대 1분 늦게** 보인다.
 * 바꾼 인스턴스는 [evict]로 곧바로 반영한다.
 *
 * 플래그 전체를 한 번에 읽어 한 덩어리로 캐시한다. 행이 몇 개뿐이라 키별로 나눌 이유가 없고,
 * 한 덩어리여야 같은 순간에 읽은 값끼리 어긋나지 않는다.
 */
@Component
class FeatureFlags(
    private val featureFlagRepository: FeatureFlagRepository,
) {
    /** 단조 시계(나노초). 테스트가 캐시 만료를 흉내 내려고 바꾼다. */
    internal var ticker: () -> Long = System::nanoTime

    @Volatile
    private var cached: Snapshot? = null

    fun isEnabled(key: FeatureFlagKey): Boolean = snapshot().states[key]?.enabled ?: false

    /** 켜져 있으면 마지막으로 켠 시각, 꺼져 있으면 null. 켜기 전부터 진행 중이던 대상을 거를 때 쓴다. */
    fun enabledAt(key: FeatureFlagKey): LocalDateTime? =
        snapshot().states[key]?.takeIf { it.enabled }?.enabledAt

    /** 이 인스턴스의 캐시를 버린다. 다음 조회가 DB 를 다시 읽는다. */
    fun evict() {
        cached = null
    }

    private fun snapshot(): Snapshot {
        val now = ticker()
        cached?.takeIf { now - it.loadedAtNanos < TTL.toNanos() }?.let { return it }

        val states = featureFlagRepository.findAll().associate { it.key to State(it.enabled, it.enabledAt) }
        return Snapshot(states, now).also { cached = it }
    }

    private data class State(val enabled: Boolean, val enabledAt: LocalDateTime?)

    private class Snapshot(val states: Map<FeatureFlagKey, State>, val loadedAtNanos: Long)

    companion object {
        val TTL: Duration = Duration.ofMinutes(1)
    }
}
