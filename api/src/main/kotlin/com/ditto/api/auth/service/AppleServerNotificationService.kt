package com.ditto.api.auth.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.user.service.LeaveProgressChecker
import com.ditto.api.user.service.MemberLeaveProcessor
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.apple.AppleServerNotification
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 애플 계정 삭제와 연결 해제는 애플 쪽에서 이미 끝난 일이라 둘 다 탈퇴로 반영한다.
 * 진행 중인 매칭·채팅이 있으면 세션만 끊고 탈퇴를 미룬다. 상대가 탈퇴자와 같은 방에 남는 상태를 만들지 않기 위해서다.
 * 같은 알림이 다시 오거나 이미 탈퇴한 회원이면 아무것도 하지 않는다. 우리가 탈퇴 때 애플 토큰을 폐기하면 애플이 연결 해제 알림을 되돌려 보낸다.
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
            -> log.info { "애플 이메일 전달 설정 변경 알림: ${notification.eventType}" }
            AppleServerNotificationType.UNKNOWN ->
                log.warn { "처리하지 않는 애플 서버 알림: ${notification.eventType}" }
        }
    }

    private fun leaveOrDefer(notification: AppleServerNotification, reason: String) {
        val account = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, notification.subject)
        val member = account?.let { memberRepository.findWithLockById(it.memberId) }
        if (account == null || member == null) {
            log.info { "애플 서버 알림(${notification.eventType}) 대상 회원이 없다." }
            return
        }
        // 애플 쪽에서 이미 끊겨 저장해 둔 애플 토큰은 더 쓸 수 없다.
        account.clearProviderToken()

        if (member.isLeft()) {
            log.info { "애플 서버 알림(${notification.eventType}): 이미 탈퇴한 회원이다. memberId=${member.id}" }
            return
        }

        if (leaveProgressChecker.hasInProgress(member.id, serverTimeProvider.now())) {
            member.deferLeave(reason)
            refreshTokenRepository.deleteAllByMemberId(member.id)
            log.info { "애플 서버 알림(${notification.eventType}): 진행 중이라 탈퇴를 미룬다. memberId=${member.id}" }
            return
        }

        memberLeaveProcessor.leave(member, reason = reason)
        log.info { "애플 서버 알림(${notification.eventType}): 탈퇴 처리했다. memberId=${member.id}" }
    }

    companion object {
        private val log = KotlinLogging.logger {}
        const val LEAVE_REASON_ACCOUNT_DELETED = "APPLE_ACCOUNT_DELETED"
        const val LEAVE_REASON_CONSENT_REVOKED = "APPLE_CONSENT_REVOKED"
    }
}
