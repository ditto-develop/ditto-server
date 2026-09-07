package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProviderFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class AppleWebAuthorizationUrlProviderTest : FreeSpec(
    {
        val properties = AppleOAuthProperties(
            clientIds = listOf("pics.ditto.app", "pics.ditto.web"),
            webClientId = "pics.ditto.web",
            webRedirectUri = "https://api.ditto.pics/api/v1/users/social-login/apple/callback",
        )
        val provider = AppleWebAuthorizationUrlProvider(properties)

        "인가 URL" - {
            "애플 인가 주소로 시작한다" {
                provider.getAuthorizationUrl() shouldStartWith "https://appleid.apple.com/auth/authorize"
            }

            "client_id 는 앱 번들 ID가 아니라 Services ID 다" {
                provider.getAuthorizationUrl() shouldContain "client_id=pics.ditto.web"
            }

            "scope 를 요청하므로 response_mode 는 form_post 여야 한다 (콜백이 POST 로 오는 이유)" {
                val url = provider.getAuthorizationUrl()

                url shouldContain "scope=name%20email"
                url shouldContain "response_mode=form_post"
            }

            "response_type 에 id_token 을 포함해 콜백에서 바로 검증한다 (코드 교환 없음)" {
                provider.getAuthorizationUrl() shouldContain "response_type=code%20id_token"
            }

            "redirect_uri 는 콘솔에 등록한 Return URL 그대로 실린다" {
                // 쿼리 값의 ':' '/' 는 RFC 3986 상 인코딩이 필요 없어 그대로 남는다.
                provider.getAuthorizationUrl() shouldContain
                    "redirect_uri=https://api.ditto.pics/api/v1/users/social-login/apple/callback"
            }
        }

        "SocialAuthorizationUrlProviderFactory" - {
            "등록된 제공자의 인가 URL 제공자를 준다" {
                val factory = SocialAuthorizationUrlProviderFactory(mapOf(SocialProvider.APPLE to provider))

                factory.getProvider(SocialProvider.APPLE) shouldBe provider
            }

            "등록되지 않은 제공자면 지원하지 않는 제공자로 알린다" {
                val factory = SocialAuthorizationUrlProviderFactory(emptyMap())

                val exception = shouldThrow<ErrorException> { factory.getProvider(SocialProvider.KAKAO) }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }
    },
)
