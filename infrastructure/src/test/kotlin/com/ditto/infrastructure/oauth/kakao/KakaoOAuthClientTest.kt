package com.ditto.infrastructure.oauth.kakao

import com.ditto.infrastructure.oauth.constants.OAuthConstants
import com.ditto.infrastructure.oauth.kakao.dto.KakaoTokenResponse
import com.ditto.infrastructure.oauth.kakao.dto.KakaoUserResponse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.util.MultiValueMap

class KakaoOAuthClientTest : FreeSpec(
    {
        val properties = KakaoOAuthProperties(
            clientId = "test-client-id",
            clientSecret = "test-secret",
            redirectUri = "http://localhost:8080/callback",
        )
        val apiSender = mockk<KakaoApiSender>()
        val client = KakaoOAuthClient(properties, apiSender)

        "getAuthorizationUrl" - {
            "카카오 인가 URL을 생성한다" {
                val url = client.getAuthorizationUrl()

                url shouldContain "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}"
                url shouldContain "${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}"
                url shouldContain "${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}"
            }
        }

        "getAccessToken" - {
            "인가 코드로 액세스 토큰을 반환한다" {
                every { apiSender.getToken(any<MultiValueMap<String, String>>()) } returns KakaoTokenResponse(
                    accessToken = "mock-access-token",
                    tokenType = "bearer",
                    expiresIn = 3600,
                )

                val token = client.getAccessToken("auth-code")

                token shouldBe "mock-access-token"
                verify { apiSender.getToken(any<MultiValueMap<String, String>>()) }
            }

            "client_secret이 있으면 토큰 요청 파라미터에 포함한다" {
                val capturedParams = mutableListOf<MultiValueMap<String, String>>()
                every { apiSender.getToken(capture(capturedParams)) } returns KakaoTokenResponse(
                    accessToken = "token",
                    tokenType = "bearer",
                    expiresIn = 3600,
                )

                client.getAccessToken("auth-code")

                val params = capturedParams.first()
                params.getFirst(OAuthConstants.PARAM_CLIENT_SECRET) shouldBe "test-secret"
            }

            "client_secret이 비어있으면 토큰 요청 파라미터에 포함하지 않는다" {
                val noSecretClient = KakaoOAuthClient(
                    properties = KakaoOAuthProperties(
                        clientId = "test-id",
                        clientSecret = "",
                        redirectUri = "http://localhost:8080/callback",
                    ),
                    client = apiSender,
                )
                val capturedParams = mutableListOf<MultiValueMap<String, String>>()
                every { apiSender.getToken(capture(capturedParams)) } returns KakaoTokenResponse(
                    accessToken = "token",
                    tokenType = "bearer",
                    expiresIn = 3600,
                )

                noSecretClient.getAccessToken("auth-code")

                val params = capturedParams.first()
                params.containsKey(OAuthConstants.PARAM_CLIENT_SECRET) shouldBe false
            }
        }

        "getUserInfo" - {
            "닉네임이 있으면 해당 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = KakaoUserResponse.KakaoAccount(
                        profile = KakaoUserResponse.KakaoProfile(nickname = "카카오유저"),
                    ),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.id shouldBe "12345"
                userInfo.nickname shouldBe "카카오유저"
            }

            "닉네임이 없으면 기본 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = KakaoUserResponse.KakaoAccount(
                        profile = KakaoUserResponse.KakaoProfile(nickname = null),
                    ),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }

            "프로필이 없으면 기본 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = KakaoUserResponse.KakaoAccount(profile = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }

            "kakaoAccount가 없으면 기본 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = null,
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }
        }
    },
)
