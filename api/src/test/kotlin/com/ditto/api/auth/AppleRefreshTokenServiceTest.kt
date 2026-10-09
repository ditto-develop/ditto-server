package com.ditto.api.auth

import com.ditto.api.auth.service.AppleRefreshTokenService
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.apple.AppleOAuthProperties
import com.ditto.infrastructure.oauth.apple.AppleTokenClient
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

// 애플 실패는 페이크 클라이언트로 만들 수 없어 클라이언트만 MockK 로 바꿔 본다.
class AppleRefreshTokenServiceTest : FreeSpec(
    {
        val properties = AppleOAuthProperties(
            webClientId = "pics.ditto.web",
            webRedirectUri = "https://api.ditto.pics/api/v1/users/social-login/APPLE/callback",
        )

        fun userInfo(clientId: String?) = OAuthUserInfo(
            id = "apple-sub",
            nickname = "닉네임",
            email = null,
            name = null,
            phoneNumber = null,
            gender = null,
            clientId = clientId,
        )

        fun appleAccount() = SocialAccount.create(memberId = 1L, provider = SocialProvider.APPLE, providerUserId = "apple-sub")

        "애플이 교환을 거절해도 예외 없이 넘어가고 아무것도 저장하지 않는다" {
            val tokenClient = mockk<AppleTokenClient>()
            every { tokenClient.exchangeCode(any(), any(), any()) } throws
                HttpClientErrorException(HttpStatus.BAD_REQUEST, "invalid_grant")
            val repository = mockk<SocialAccountRepository>(relaxed = true)

            AppleRefreshTokenService(tokenClient, repository, properties)
                .exchangeAndStore(userInfo(clientId = "pics.ditto.app"), "expired-code")

            verify(exactly = 0) { repository.save(any()) }
        }

        "웹 Services ID 로 받은 코드는 redirect_uri 를 붙여 교환하고 받은 client_id 와 함께 저장한다" {
            val tokenClient = mockk<AppleTokenClient>()
            every {
                tokenClient.exchangeCode("web-code", "pics.ditto.web", properties.webRedirectUri)
            } returns "apple-refresh"
            val account = appleAccount()
            val repository = mockk<SocialAccountRepository>()
            every { repository.findByProviderAndProviderUserId(SocialProvider.APPLE, "apple-sub") } returns account
            every { repository.save(account) } returns account

            AppleRefreshTokenService(tokenClient, repository, properties)
                .exchangeAndStore(userInfo(clientId = "pics.ditto.web"), "web-code")

            account.providerRefreshToken shouldBe "apple-refresh"
            account.providerClientId shouldBe "pics.ditto.web"
        }

        "앱 번들 ID 로 받은 코드는 redirect_uri 없이 교환한다" {
            val tokenClient = mockk<AppleTokenClient>()
            every { tokenClient.exchangeCode("app-code", "pics.ditto.app", null) } returns "apple-refresh"
            val repository = mockk<SocialAccountRepository>(relaxed = true)
            every { repository.findByProviderAndProviderUserId(SocialProvider.APPLE, "apple-sub") } returns appleAccount()
            every { repository.save(any<SocialAccount>()) } returnsArgument 0

            AppleRefreshTokenService(tokenClient, repository, properties)
                .exchangeAndStore(userInfo(clientId = "pics.ditto.app"), "app-code")

            verify(exactly = 1) { tokenClient.exchangeCode("app-code", "pics.ditto.app", null) }
        }

        "client_id 를 모르면 교환하지 않는다" {
            val tokenClient = mockk<AppleTokenClient>()
            val repository = mockk<SocialAccountRepository>(relaxed = true)

            AppleRefreshTokenService(tokenClient, repository, properties)
                .exchangeAndStore(userInfo(clientId = null), "code")

            verify(exactly = 0) { tokenClient.exchangeCode(any(), any(), any()) }
        }
    },
)
