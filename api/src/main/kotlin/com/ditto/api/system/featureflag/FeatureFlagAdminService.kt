package com.ditto.api.system.featureflag

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

/** 어드민 기능 플래그 화면의 조회·전환. 읽기 경로는 [FeatureFlags]를 쓴다(캐시). */
@Service
@Transactional(readOnly = true)
class FeatureFlagAdminService(
    private val featureFlagRepository: FeatureFlagRepository,
    private val featureFlags: FeatureFlags,
    private val serverTimeProvider: ServerTimeProvider,
) {
    /** 정의된 플래그 전부. 행이 없는 플래그는 꺼진 것으로 보여 준다. */
    fun list(): List<FeatureFlagView> {
        val rowsByKey = featureFlagRepository.findAll().associateBy { it.key }
        return FeatureFlagKey.entries.map { key ->
            val row = rowsByKey[key]
            FeatureFlagView(
                key = key,
                enabled = row?.enabled ?: false,
                enabledAt = row?.enabledAt,
                updatedAt = row?.updatedAt,
                updatedByMemberId = row?.updatedByMemberId,
            )
        }
    }

    /**
     * 켜거나 끈다. 켠 시각은 서버 시각([ServerTimeProvider])으로 남긴다 — "켠 뒤 열린 방" 판정이 비교하는 방의
     * 개방 시각도 서버 시각이라, QA 가 시각을 옮겨 둔 채 켜도 두 값이 같은 시계를 본다.
     *
     * 이 인스턴스의 캐시는 **커밋 뒤에** 버린다. 커밋 전에 버리면 그 사이 다른 요청이 옛 값을 다시 캐시한다.
     */
    @Transactional
    fun set(key: FeatureFlagKey, enabled: Boolean, adminMemberId: Long) {
        val flag = featureFlagRepository.findByKey(key) ?: featureFlagRepository.save(FeatureFlag.of(key))
        if (enabled) flag.enable(serverTimeProvider.now(), adminMemberId) else flag.disable(adminMemberId)
        evictAfterCommit()
    }

    private fun evictAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = featureFlags.evict()
            },
        )
    }
}

data class FeatureFlagView(
    val key: FeatureFlagKey,
    val enabled: Boolean,
    val enabledAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val updatedByMemberId: Long?,
)
