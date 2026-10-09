package com.ditto.infrastructure.oauth.apple

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.util.MultiValueMap

class AppleTokenHttpClientTest : FreeSpec(
    {
        val configured = AppleOAuthProperties(teamId = "TEAM123456", keyId = "KEY1234567", privateKey = "pem")
        val secretGenerator = mockk<AppleClientSecretGenerator>()
        every { secretGenerator.generate(any()) } answers { "secret-for-${firstArg<String>()}" }

        "인가 코드 교환" - {
            "코드를 넘기고 받은 refresh token 을 돌려준다" {
                val sender = mockk<AppleAuthSender>()
                val params = slot<MultiValueMap<String, String>>()
                every { sender.getToken(capture(params)) } returns AppleTokenResponse(refreshToken = "apple-refresh")

                val refreshToken = AppleTokenHttpClient(configured, secretGenerator, sender)
                    .exchangeCode(code = "auth-code", clientId = "pics.ditto.app")

                refreshToken shouldBe "apple-refresh"
                params.captured.getFirst("grant_type") shouldBe "authorization_code"
                params.captured.getFirst("code") shouldBe "auth-code"
                params.captured.getFirst("client_id") shouldBe "pics.ditto.app"
                params.captured.getFirst("client_secret") shouldBe "secret-for-pics.ditto.app"
                params.captured.containsKey("redirect_uri") shouldBe false
            }

            "웹 코드는 redirect_uri 를 함께 보낸다" {
                val sender = mockk<AppleAuthSender>()
                val params = slot<MultiValueMap<String, String>>()
                every { sender.getToken(capture(params)) } returns AppleTokenResponse(refreshToken = "apple-refresh")

                AppleTokenHttpClient(configured, secretGenerator, sender).exchangeCode(
                    code = "web-code",
                    clientId = "pics.ditto.web",
                    redirectUri = "https://api.ditto.pics/api/v1/users/social-login/APPLE/callback",
                )

                params.captured.getFirst("redirect_uri") shouldBe
                    "https://api.ditto.pics/api/v1/users/social-login/APPLE/callback"
            }

            "비밀값이 없으면 애플을 부르지 않고 null 을 돌려준다" {
                val sender = mockk<AppleAuthSender>()

                val refreshToken = AppleTokenHttpClient(AppleOAuthProperties(), secretGenerator, sender)
                    .exchangeCode(code = "auth-code", clientId = "pics.ditto.app")

                refreshToken.shouldBeNull()
                verify(exactly = 0) { sender.getToken(any()) }
            }
        }

        "토큰 폐기" - {
            "refresh token 과 발급받은 clientId 로 폐기한다" {
                val sender = mockk<AppleAuthSender>()
                val params = slot<MultiValueMap<String, String>>()
                every { sender.revoke(capture(params)) } just runs

                AppleTokenHttpClient(configured, secretGenerator, sender)
                    .revoke(refreshToken = "apple-refresh", clientId = "pics.ditto.web")

                params.captured.getFirst("token") shouldBe "apple-refresh"
                params.captured.getFirst("token_type_hint") shouldBe "refresh_token"
                params.captured.getFirst("client_id") shouldBe "pics.ditto.web"
                params.captured.getFirst("client_secret") shouldBe "secret-for-pics.ditto.web"
            }

            "비밀값이 없으면 애플을 부르지 않는다" {
                val sender = mockk<AppleAuthSender>()

                AppleTokenHttpClient(AppleOAuthProperties(), secretGenerator, sender)
                    .revoke(refreshToken = "apple-refresh", clientId = "pics.ditto.app")

                verify(exactly = 0) { sender.revoke(any()) }
            }
        }
    },
)
