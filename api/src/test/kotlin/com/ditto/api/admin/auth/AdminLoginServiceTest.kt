package com.ditto.api.admin.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialAuthenticatorFactory
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class AdminLoginServiceTest : FreeSpec({
    val client = mockk<OAuthClient>()
    val appleUrlProvider = mockk<SocialAuthorizationUrlProvider>()
    val appleAuthenticator = mockk<NativeSocialAuthenticator>()
    val nativeSocialAuthenticatorFactory = mockk<NativeSocialAuthenticatorFactory>()
    val memberSocialAccountService = mockk<MemberSocialAccountService>()
    val service = AdminLoginService(
        client,
        appleUrlProvider,
        nativeSocialAuthenticatorFactory,
        memberSocialAccountService,
    )

    val userInfo = OAuthUserInfo(
        id = "kakao-1", nickname = "n", email = "admin@ditto.pics", name = "관리자", phoneNumber = null, gender = null,
    )
    val appleUserInfo = userInfo.copy(id = "apple-sub-1")

    every { nativeSocialAuthenticatorFactory.getAuthenticator(SocialProvider.APPLE) } returns appleAuthenticator

    "authorizationUrl 은 카카오 인가 URL 을 반환한다" {
        every { client.getAuthorizationUrl() } returns "https://kauth.kakao.com/authorize"
        service.authorizationUrl() shouldBe "https://kauth.kakao.com/authorize"
    }

    "appleAuthorizationUrl 은 어드민 Return URL 이 박힌 애플 인가 URL 을 반환한다" {
        every { appleUrlProvider.getAuthorizationUrl() } returns "https://appleid.apple.com/auth/authorize?x=1"
        service.appleAuthorizationUrl() shouldBe "https://appleid.apple.com/auth/authorize?x=1"
    }

    "ADMIN 회원이면 AdminPrincipal 을 반환한다" {
        every { client.getAccessToken("code") } returns "token"
        every { client.getUserInfo("token") } returns userInfo
        val admin = MemberFixture.create(role = MemberRole.ADMIN, id = 10L).apply { activate() }
        every { memberSocialAccountService.findMemberBySocial(SocialProvider.KAKAO, "kakao-1") } returns admin

        val principal = service.login("code")

        principal.memberId shouldBe 10L
        principal.name shouldBe admin.name
    }

    "등록되지 않은 회원이면 거부한다" {
        every { client.getAccessToken(any()) } returns "token"
        every { client.getUserInfo(any()) } returns userInfo
        every { memberSocialAccountService.findMemberBySocial(any(), any()) } returns null

        shouldThrow<AdminLoginDeniedException> { service.login("code") }
    }

    "관리자가 아니면 거부한다" {
        every { client.getAccessToken(any()) } returns "token"
        every { client.getUserInfo(any()) } returns userInfo
        every { memberSocialAccountService.findMemberBySocial(any(), any()) } returns
            MemberFixture.create(role = MemberRole.USER, id = 11L)

        shouldThrow<AdminLoginDeniedException> { service.login("code") }
    }

    "애플 로그인" - {
        "ID 토큰의 sub 로 찾은 ADMIN 회원이면 AdminPrincipal 을 반환한다" {
            every { appleAuthenticator.authenticate(any()) } returns appleUserInfo
            val admin = MemberFixture.create(role = MemberRole.ADMIN, id = 20L).apply { activate() }
            every {
                memberSocialAccountService.findMemberBySocial(SocialProvider.APPLE, "apple-sub-1")
            } returns admin

            service.loginWithAppleIdToken("id-token").memberId shouldBe 20L
        }

        "애플로 연결된 회원이 없으면 거부한다" {
            every { appleAuthenticator.authenticate(any()) } returns appleUserInfo
            every { memberSocialAccountService.findMemberBySocial(SocialProvider.APPLE, any()) } returns null

            shouldThrow<AdminLoginDeniedException> { service.loginWithAppleIdToken("id-token") }
        }

        "관리자가 아니면 거부한다" {
            every { appleAuthenticator.authenticate(any()) } returns appleUserInfo
            every { memberSocialAccountService.findMemberBySocial(SocialProvider.APPLE, any()) } returns
                MemberFixture.create(role = MemberRole.USER, id = 21L)

            shouldThrow<AdminLoginDeniedException> { service.loginWithAppleIdToken("id-token") }
        }

        // 서버 렌더 화면이라 WarnException 이 새어나가면 로그인 페이지 대신 JSON 이 노출된다.
        // 콘솔에 Return URL·Services ID 가 등록되기 전 구간이 정확히 이 경로다.
        "ID 토큰 검증에 실패하면 로그인 거부로 바꿔 던진다" {
            every { appleAuthenticator.authenticate(any()) } throws
                WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)

            shouldThrow<AdminLoginDeniedException> { service.loginWithAppleIdToken("id-token") }
        }
    }
})
