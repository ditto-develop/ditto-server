package com.ditto.api.auth

import com.ditto.api.auth.service.AppleServerNotificationService
import com.ditto.api.auth.service.AuthService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.apple.AppleServerNotification
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class AppleServerNotificationServiceTest(
    private val appleServerNotificationService: AppleServerNotificationService,
    private val authService: AuthService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveAppleMember(nickname: String, subject: String): Member {
        val member = memberRepository.save(Member(nickname = nickname).apply { activate() })
        socialAccountRepository.save(
            SocialAccount.create(memberId = member.id, provider = SocialProvider.APPLE, providerUserId = subject),
        )
        return member
    }

    fun notification(type: AppleServerNotificationType, rawEventType: String, subject: String) =
        AppleServerNotification(type = type, rawEventType = rawEventType, subject = subject)

    fun accountDeleted(subject: String) =
        notification(AppleServerNotificationType.ACCOUNT_DELETED, "account-delete", subject)

    fun reload(member: Member): Member = memberRepository.findById(member.id).orElseThrow()

    fun sessionCount(member: Member): Int = refreshTokenRepository.findByMemberIdIn(listOf(member.id)).size

    "진행 중인 매칭·채팅이 없으면 바로 탈퇴시킨다" - {
        "계정 삭제 알림이면 애플 계정 삭제 사유로 탈퇴하고 세션과 푸시 토큰을 지운다" {
            val member = saveAppleMember("애플삭제회원", "apple-sub-deleted")
            authService.createRefreshToken(member.id)
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = "apple-phone"))

            appleServerNotificationService.handle(accountDeleted("apple-sub-deleted"))

            val left = reload(member)
            left.status shouldBe MemberStatus.LEFT
            left.leaveReason shouldBe AppleServerNotificationService.LEAVE_REASON_ACCOUNT_DELETED
            sessionCount(member) shouldBe 0
            memberDeviceRepository.findAllByMemberId(member.id) shouldBe emptyList()
        }

        "연결 해제 알림이면 연결 해제 사유로 탈퇴한다" {
            val member = saveAppleMember("애플해제회원", "apple-sub-revoked")

            appleServerNotificationService.handle(
                notification(AppleServerNotificationType.CONSENT_REVOKED, "consent-revoked", "apple-sub-revoked"),
            )

            val left = reload(member)
            left.status shouldBe MemberStatus.LEFT
            left.leaveReason shouldBe AppleServerNotificationService.LEAVE_REASON_CONSENT_REVOKED
        }

        "애플 쪽에서 이미 끊겼으니 저장해 둔 애플 토큰을 지운다" {
            val member = saveAppleMember("토큰있는애플회원", "apple-sub-token")
            val account = socialAccountRepository.findByMemberId(member.id) ?: error("소셜 계정 없음")
            account.storeProviderToken(refreshToken = "apple-refresh", clientId = "pics.ditto.app")
            socialAccountRepository.save(account)

            appleServerNotificationService.handle(accountDeleted("apple-sub-token"))

            socialAccountRepository.findByMemberId(member.id)?.providerRefreshToken.shouldBeNull()
        }
    }

    "진행 중인 매칭이 있으면 탈퇴를 미루고 세션만 끊는다" {
        val member = saveAppleMember("매칭중애플회원", "apple-sub-matching")
        val partner = memberRepository.save(Member(nickname = "매칭상대").apply { activate() })
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = member.id,
                receiverId = partner.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )
        authService.createRefreshToken(member.id)

        appleServerNotificationService.handle(accountDeleted("apple-sub-matching"))

        val deferred = reload(member)
        deferred.status shouldBe MemberStatus.ACTIVE
        deferred.deferredLeaveReason shouldBe AppleServerNotificationService.LEAVE_REASON_ACCOUNT_DELETED
        sessionCount(member) shouldBe 0
    }

    "다시 와도 안전하다" - {
        "이미 탈퇴한 회원이면 탈퇴 기록을 덮지 않는다" {
            val member = saveAppleMember("이미탈퇴한애플회원", "apple-sub-left")
            appleServerNotificationService.handle(accountDeleted("apple-sub-left"))
            val firstLeftAt = reload(member).leftAt

            appleServerNotificationService.handle(
                notification(AppleServerNotificationType.CONSENT_REVOKED, "consent-revoked", "apple-sub-left"),
            )

            val left = reload(member)
            left.leftAt shouldBe firstLeftAt
            left.leaveReason shouldBe AppleServerNotificationService.LEAVE_REASON_ACCOUNT_DELETED
        }

        "애플 계정에 연결된 회원이 없으면 아무것도 하지 않는다" {
            appleServerNotificationService.handle(accountDeleted("apple-sub-unknown"))
        }

        "같은 식별자의 카카오 계정은 건드리지 않는다" {
            val member = memberRepository.save(Member(nickname = "카카오회원").apply { activate() })
            socialAccountRepository.save(
                SocialAccount.create(memberId = member.id, provider = SocialProvider.KAKAO, providerUserId = "shared-id"),
            )

            appleServerNotificationService.handle(accountDeleted("shared-id"))

            reload(member).status shouldBe MemberStatus.ACTIVE
        }
    }

    "이메일 전달 설정 알림과 모르는 알림은 회원을 바꾸지 않는다" {
        val member = saveAppleMember("이메일설정회원", "apple-sub-email")
        authService.createRefreshToken(member.id)

        appleServerNotificationService.handle(
            notification(AppleServerNotificationType.EMAIL_DISABLED, "email-disabled", "apple-sub-email"),
        )
        appleServerNotificationService.handle(
            notification(AppleServerNotificationType.UNKNOWN, "new-event", "apple-sub-email"),
        )

        val unchanged = reload(member)
        unchanged.status shouldBe MemberStatus.ACTIVE
        unchanged.deferredLeaveReason.shouldBeNull()
        sessionCount(member) shouldBe 1
    }
})
