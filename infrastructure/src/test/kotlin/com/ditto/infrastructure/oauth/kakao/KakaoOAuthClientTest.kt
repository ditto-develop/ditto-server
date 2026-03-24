package com.ditto.infrastructure.oauth.kakao

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import com.ditto.infrastructure.support.IntegrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class KakaoOAuthClientTest(
    private val properties: KakaoOAuthProperties,
    private val oAuthClientFactory: OAuthClientFactory,
) : IntegrationTest(
    {
        val client = oAuthClientFactory.getClient(SocialProvider.KAKAO)
        
        "getAuthorizationUrl" - {
            "카카오 인가 URL을 생성한다" {
                val url = client.getAuthorizationUrl()

                url shouldContain "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}"
                url shouldContain "${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}"
                url shouldContain "${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}"
            }
        }

        "buildTokenRequestParams" - {
            "client_secret이 비어있으면 파라미터에 포함하지 않는다" {
                val clientWithoutSecret = KakaoOAuthFakeClient(
                    properties = KakaoOAuthProperties(
                        clientId = "test-id",
                        clientSecret = "",
                        redirectUri = "http://localhost:8080/callback",
                    ),
                )

                val url = clientWithoutSecret.getAuthorizationUrl()
                url shouldNotContain OAuthConstants.PARAM_CLIENT_SECRET
            }

            "client_secret이 있으면 인가 URL에는 포함되지 않는다" {
                val clientWithSecret = KakaoOAuthFakeClient(
                    properties = KakaoOAuthProperties(
                        clientId = "test-id",
                        clientSecret = "test-secret",
                        redirectUri = "http://localhost:8080/callback",
                    ),
                )

                val url = clientWithSecret.getAuthorizationUrl()
                url shouldNotContain "test-secret"
            }
        }
    },
)
