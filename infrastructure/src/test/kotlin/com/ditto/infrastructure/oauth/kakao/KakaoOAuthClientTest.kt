package com.ditto.infrastructure.oauth.kakao

import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class KakaoOAuthClientTest : FreeSpec({

    val properties = KakaoOAuthProperties(
        clientId = "test-client-id",
        clientSecret = "",
        redirectUri = "http://localhost:8080/users/social-login/kakao/callback",
    )
    val client = KakaoOAuthClient(properties)

    "getProvider" - {
        "KAKAO를 반환한다" {
            client.getProvider() shouldBe SocialProvider.KAKAO
        }
    }

    "getAuthorizationUrl" - {
        "카카오 인가 URL을 생성한다" {
            val url = client.getAuthorizationUrl()

            url shouldContain "https://kauth.kakao.com/oauth/authorize?"
            url shouldContain "${OAuthClient.PARAM_CLIENT_ID}=test-client-id"
            url shouldContain "${OAuthClient.PARAM_REDIRECT_URI}=http://localhost:8080/users/social-login/kakao/callback"
            url shouldContain "${OAuthClient.PARAM_RESPONSE_TYPE}=${OAuthClient.RESPONSE_TYPE_CODE}"
        }
    }

    "buildTokenRequestParams" - {
        "client_secret이 비어있으면 파라미터에 포함하지 않는다" {
            val clientWithoutSecret = KakaoOAuthClient(
                KakaoOAuthProperties(
                    clientId = "test-id",
                    clientSecret = "",
                    redirectUri = "http://localhost:8080/callback",
                ),
            )

            val url = clientWithoutSecret.getAuthorizationUrl()
            url shouldNotContain OAuthClient.PARAM_CLIENT_SECRET
        }

        "client_secret이 있으면 인가 URL에는 포함되지 않는다" {
            val clientWithSecret = KakaoOAuthClient(
                KakaoOAuthProperties(
                    clientId = "test-id",
                    clientSecret = "test-secret",
                    redirectUri = "http://localhost:8080/callback",
                ),
            )

            val url = clientWithSecret.getAuthorizationUrl()
            url shouldNotContain "test-secret"
        }
    }
})
