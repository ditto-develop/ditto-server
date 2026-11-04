package com.ditto.api.system.featureflag

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
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
    private val featureFlagRowCreator: FeatureFlagRowCreator,
    private val featureFlags: FeatureFlags,
    private val serverTimeProvider: ServerTimeProvider,
    private val memberRepository: MemberRepository,
    private val enableListeners: List<FeatureFlagEnableListener>,
) {
    /** 정의된 플래그 전부. 행이 없는 플래그는 꺼진 것으로 보여 준다. */
    fun list(): List<FeatureFlagView> {
        val rowsByKey = featureFlagRepository.findAll().associateBy { it.key }
        val updatersById = memberRepository.findAllById(rowsByKey.values.mapNotNull { it.updatedByMemberId }.toSet())
            .associateBy { it.id }
        return FeatureFlagKey.entries.map { key ->
            val row = rowsByKey[key]
            val updater = row?.updatedByMemberId?.let(updatersById::get)
            FeatureFlagView(
                key = key,
                enabled = row?.enabled ?: false,
                enabledAt = row?.enabledAt,
                updatedAt = row?.updatedAt,
                updatedByMemberId = row?.updatedByMemberId,
                updatedByName = updater?.name,
                updatedByEmail = updater?.email,
            )
        }
    }

    /**
     * 켜거나 끈다. 꺼져 있다가 켜지는 순간 그 플래그의 [FeatureFlagEnableListener]를 같은 트랜잭션에서 부른다.
     *
     * 행을 잠그고 바꾼다 — 겹친 켜기 두 번이 둘 다 "꺼져 있었다"로 보고 리스너를 두 번 부르지 않게 한다.
     * 행이 없으면 먼저 자기 트랜잭션에서 만든다([FeatureFlagRowCreator]). 잠금 읽기를 이 트랜잭션의 첫 조회로 둬서,
     * 그 뒤 읽기가 잠금 대기 중 커밋된 값까지 본다.
     *
     * 켠 시각(`enabled_at`)은 화면 표시용이라 어드민이 보는 서버 시각([ServerTimeProvider])으로 남긴다.
     * 이 서버의 캐시는 **커밋 뒤에** 버린다. 커밋 전에 버리면 그 사이 다른 요청이 옛 값을 다시 캐시한다.
     */
    @Transactional
    fun set(key: FeatureFlagKey, enabled: Boolean, adminMemberId: Long) {
        ensureRow(key)
        val flag = featureFlagRepository.findWithLockByKey(key)
            ?: throw IllegalStateException("기능 플래그 행을 만들지 못했다: $key")
        if (enabled) {
            if (flag.enable(serverTimeProvider.now(), adminMemberId)) {
                enableListeners.filter { it.key == key }.forEach { it.onEnabled() }
            }
        } else {
            flag.disable(adminMemberId)
        }
        evictAfterCommit()
    }

    /** 동시에 처음 켜기를 눌러 행 만들기에 졌으면 넘긴다 — 이긴 쪽 행을 아래 잠금 조회가 읽는다. */
    private fun ensureRow(key: FeatureFlagKey) {
        try {
            featureFlagRowCreator.createIfAbsent(key)
        } catch (e: DataIntegrityViolationException) {
            logger.info { "기능 플래그 행을 동시에 만들어 이긴 쪽 행을 쓴다: $key" }
        }
    }

    private fun evictAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = featureFlags.evict()
            },
        )
    }
}

private val logger = KotlinLogging.logger {}

data class FeatureFlagView(
    val key: FeatureFlagKey,
    val enabled: Boolean,
    val enabledAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val updatedByMemberId: Long?,
    val updatedByName: String?,
    val updatedByEmail: String?,
)
