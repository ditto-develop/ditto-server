package com.ditto.api.user.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val log = KotlinLogging.logger {}

/**
 * 탈퇴 후 보존 기간이 지난 회원을 완전 삭제한다 — "30일이 지나면 모든 데이터가 완전히 삭제됩니다".
 *
 * **되돌릴 수 없는 삭제**라서 안전장치를 셋 둔다.
 * 1. [dryRun]이 참이면 대상만 로그로 남기고 삭제하지 않는다(기본값 — 운영 투입 전 관찰용).
 * 2. [batchLimit]으로 한 번에 지우는 건수를 제한한다(사고 시 피해 범위 제한).
 * 3. 삭제 대상 ID·탈퇴 일시를 항상 로그로 남긴다(사후 추적).
 */
@Service
class LeftMemberPurgeService(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val notificationRepository: NotificationRepository,
    private val serverTimeProvider: ServerTimeProvider,
    @Value("\${ditto.member.purge.dry-run:true}") private val dryRun: Boolean,
    @Value("\${ditto.member.purge.batch-limit:100}") private val batchLimit: Int,
    @Value("\${ditto.member.purge.retention-days:30}") private val retentionDays: Long,
) {

    /** 매일 04:00에 실행한다 — 트래픽이 가장 낮은 시간대다. */
    @Scheduled(cron = "0 0 4 * * *")
    fun purgeExpired() {
        purge()
    }

    @Transactional
    fun purge(): Int {
        val now = serverTimeProvider.now()
        val expired = memberRepository
            .findAllByStatusAndLeftAtLessThanEqual(MemberStatus.LEFT, now.minusDays(retentionDays))
            .take(batchLimit)

        if (expired.isEmpty()) {
            return 0
        }

        log.info {
            "탈퇴 보존 기간 경과 회원 ${expired.size}명 (dryRun=$dryRun, retentionDays=$retentionDays): " +
                expired.joinToString { "id=${it.id}(leftAt=${it.leftAt})" }
        }
        if (dryRun) {
            return 0
        }

        expired.forEach { member ->
            refreshTokenRepository.deleteAllByMemberId(member.id)
            // 알림 본문에는 닉네임·메시지 미리보기가 들어 있어 회원과 함께 지운다.
            notificationRepository.deleteAllByMemberId(member.id)
            socialAccountRepository.findByMemberId(member.id)?.let { socialAccountRepository.delete(it) }
            memberRepository.delete(member)
        }
        log.info { "탈퇴 회원 ${expired.size}명 완전 삭제 완료" }
        return expired.size
    }
}
