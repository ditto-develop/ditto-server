package com.ditto.api.admin.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class AdminLoginServiceTest : FreeSpec({
    val client = mockk<OAuthClient>()
    val memberSocialAccountService = mockk<MemberSocialAccountService>()
    val service = AdminLoginService(client, memberSocialAccountService)

    val userInfo = OAuthUserInfo(
        id = "kakao-1", nickname = "n", email = "admin@ditto.pics", name = "관리자", phoneNumber = null, gender = null,
    )

    "authorizationUrl 은 카카오 인가 URL 을 반환한다" {
        every { client.getAuthorizationUrl() } returns "https://kauth.kakao.com/authorize"
        service.authorizationUrl() shouldBe "https://kauth.kakao.com/authorize"
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
})
