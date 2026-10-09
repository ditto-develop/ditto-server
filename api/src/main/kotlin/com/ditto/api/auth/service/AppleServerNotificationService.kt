package com.ditto.api.auth.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.user.service.LeaveProgressChecker
import com.ditto.api.user.service.MemberLeaveProcessor
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.apple.AppleServerNotification
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 애플 계정 삭제와 연결 해제는 애플에서 이미 끝난 일이라 둘 다 탈퇴로 반영한다.
 * 우리가 탈퇴 때 토큰을 폐기해도 애플이 연결 해제 알림을 돌려보내므로, 이미 탈퇴한 회원이면 아무것도 하지 않는다.
 */
@Service
class AppleServerNotificationService(
    private val socialAccountRepository: SocialAccountRepository,
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val leaveProgressChecker: LeaveProgressChecker,
    private val memberLeaveProcessor: MemberLeaveProcessor,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @Transactional
    fun handle(notification: AppleServerNotification) {
        when (notification.type) {
            AppleServerNotificationType.ACCOUNT_DELETED -> leaveOrDefer(notification, LEAVE_REASON_ACCOUNT_DELETED)
            AppleServerNotificationType.CONSENT_REVOKED -> leaveOrDefer(notification, LEAVE_REASON_CONSENT_REVOKED)
            // 메일을 보내는 곳이 없어 저장하지 않는다.
            AppleServerNotificationType.EMAIL_DISABLED,
            AppleServerNotificationType.EMAIL_ENABLED,
            -> log.info { "애플 이메일 전달 설정 변경 알림: ${notification.rawEventType}" }
            AppleServerNotificationType.UNKNOWN ->
                log.warn { "처리하지 않는 애플 서버 알림: ${notification.rawEventType}" }
        }
    }

    private fun leaveOrDefer(notification: AppleServerNotification, reason: String) {
        val logPrefix = "애플 서버 알림(${notification.rawEventType})"
        val (account, member) = findAppleAccountWithLockedMember(notification.subject) ?: run {
            log.info { "$logPrefix 대상 회원이 없다." }
            return
        }
        // 애플에서 이미 끊겨 저장해 둔 애플 토큰은 더 쓸 수 없다.
        account.clearProviderToken()

        if (member.isLeft()) {
            log.info { "$logPrefix: 이미 탈퇴한 회원이다. memberId=${member.id}" }
            return
        }
        if (leaveProgressChecker.hasInProgress(member.id, serverTimeProvider.now())) {
            deferLeaveAndEndSessions(member, reason)
            log.info { "$logPrefix: 진행 중이라 탈퇴를 미룬다. memberId=${member.id}" }
            return
        }

        memberLeaveProcessor.leave(member, reason = reason)
        log.info { "$logPrefix: 탈퇴 처리했다. memberId=${member.id}" }
    }

    private fun findAppleAccountWithLockedMember(subject: String): Pair<SocialAccount, Member>? {
        val account = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, subject)
            ?: return null
        val member = memberRepository.findWithLockById(account.memberId) ?: return null
        return account to member
    }

    private fun deferLeaveAndEndSessions(member: Member, reason: String) {
        member.deferLeave(reason)
        refreshTokenRepository.deleteAllByMemberId(member.id)
    }

    companion object {
        private val log = KotlinLogging.logger {}
        const val LEAVE_REASON_ACCOUNT_DELETED = "APPLE_ACCOUNT_DELETED"
        const val LEAVE_REASON_CONSENT_REVOKED = "APPLE_CONSENT_REVOKED"
    }
}
