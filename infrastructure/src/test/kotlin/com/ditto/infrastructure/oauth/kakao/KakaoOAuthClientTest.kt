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
import java.time.LocalDate

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

            "scope에 account_email·birthyear·birthday를 포함한다" {
                val url = client.getAuthorizationUrl()

                url shouldContain "${OAuthConstants.PARAM_SCOPE}=account_email,birthyear,birthday"
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
            fun kakaoAccount(
                nickname: String? = "카카오유저",
                email: String? = "user@kakao.com",
                birthyear: String? = null,
                birthday: String? = null,
            ) = KakaoUserResponse.KakaoAccount(
                profile = KakaoUserResponse.KakaoProfile(nickname = nickname),
                email = email,
                birthyear = birthyear,
                birthday = birthday,
            )

            "닉네임과 이메일이 있으면 해당 값을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(nickname = "카카오유저", email = "user@kakao.com"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.id shouldBe "12345"
                userInfo.nickname shouldBe "카카오유저"
                userInfo.email shouldBe "user@kakao.com"
            }

            "닉네임이 없으면 기본 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(nickname = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }

            "프로필이 없으면 기본 닉네임을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = KakaoUserResponse.KakaoAccount(
                        profile = null,
                        email = "user@kakao.com",
                        birthyear = null,
                        birthday = null,
                    ),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }

            "이메일이 없으면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(email = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.email shouldBe null
            }

            "kakaoAccount가 없으면 email은 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = null,
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.email shouldBe null
            }

            "birthyear와 birthday가 모두 있으면 음력/양력 구분 없이 생년월일을 저장한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = "1990", birthday = "0315"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe LocalDate.of(1990, 3, 15)
            }

            "birthyear가 없으면 생년월일은 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = null, birthday = "0315"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe null
            }

            "birthday가 없으면 생년월일은 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = "1990", birthday = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe null
            }

            "생년월일 포맷이 잘못되면 예외 없이 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = "1990", birthday = "9999"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe null
            }

            "출생연도가 숫자가 아니면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = "abcd", birthday = "0315"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe null
            }

            "생일이 MMDD(4자리) 형식이 아니면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(birthyear = "1990", birthday = "315"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.birthDate shouldBe null
            }
        }
    },
)
