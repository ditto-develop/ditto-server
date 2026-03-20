package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.Member
import com.ditto.domain.member.MemberRepository
import com.ditto.domain.socialaccount.SocialAccount
import com.ditto.domain.socialaccount.SocialAccountRepository
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class OAuthServiceTest : FreeSpec({

    val oAuthClient = mockk<OAuthClient>()
    val memberRepository = mockk<MemberRepository>()
    val socialAccountRepository = mockk<SocialAccountRepository>()
    val jwtTokenProvider = mockk<JwtTokenProvider>()

    every { oAuthClient.getProvider() } returns SocialProvider.KAKAO

    val oAuthService = OAuthService(
        oAuthClients = listOf(oAuthClient),
        memberRepository = memberRepository,
        socialAccountRepository = socialAccountRepository,
        jwtTokenProvider = jwtTokenProvider,
    )

    beforeEach {
        clearAllMocks(answers = false)
        every { oAuthClient.getProvider() } returns SocialProvider.KAKAO
    }

    fun stubOAuthClientReturns(userInfo: OAuthUserInfo) {
        every { oAuthClient.getAccessToken(any()) } returns "kakao-access-token"
        every { oAuthClient.getUserInfo("kakao-access-token") } returns userInfo
    }

    "인가 URL 조회" - {
        "카카오 인가 URL을 반환한다" {
            val expectedUrl = "https://kauth.kakao.com/oauth/authorize?client_id=test"
            every { oAuthClient.getAuthorizationUrl() } returns expectedUrl

            oAuthService.getAuthorizationUrl(SocialProvider.KAKAO) shouldBe expectedUrl
        }

        "지원하지 않는 제공자면 예외가 발생한다" {
            val serviceWithNoClients = OAuthService(
                oAuthClients = emptyList(),
                memberRepository = memberRepository,
                socialAccountRepository = socialAccountRepository,
                jwtTokenProvider = jwtTokenProvider,
            )

            val exception = shouldThrow<WarnException> {
                serviceWithNoClients.getAuthorizationUrl(SocialProvider.KAKAO)
            }
            exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
        }
    }

    "소셜 로그인" - {
        val userInfo = OAuthUserInfo(id = "12345", nickname = "테스트유저")

        "신규 사용자면 회원가입 후 JWT를 발급한다" {
            stubOAuthClientReturns(userInfo)
            every {
                socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "12345")
            } returns null
            every { memberRepository.save(any()) } returns Member(nickname = "테스트유저", id = 1L)
            every { socialAccountRepository.save(any()) } returns mockk()
            every { jwtTokenProvider.generateToken(1L) } returns "jwt-token"

            val result = oAuthService.login(SocialProvider.KAKAO, "auth-code")

            result.accessToken shouldBe "jwt-token"
            verify { memberRepository.save(any()) }
            verify { socialAccountRepository.save(any()) }
        }

        "기존 사용자면 기존 회원으로 JWT를 발급한다" {
            stubOAuthClientReturns(userInfo)
            every {
                socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "12345")
            } returns SocialAccount.create(memberId = 1L, provider = SocialProvider.KAKAO, providerUserId = "12345")
            every { jwtTokenProvider.generateToken(1L) } returns "jwt-token"

            val result = oAuthService.login(SocialProvider.KAKAO, "auth-code")

            result.accessToken shouldBe "jwt-token"
            verify(exactly = 0) { memberRepository.save(any()) }
        }
    }
})
