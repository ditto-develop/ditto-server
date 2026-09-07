package com.ditto.infrastructure.oauth.kakao

import com.ditto.domain.member.entity.Gender
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import com.ditto.infrastructure.oauth.kakao.dto.KakaoTokenResponse
import com.ditto.infrastructure.oauth.kakao.dto.KakaoUserResponse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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

            "기본 동의항목은 profile_nickname 하나다 — 나머지는 비즈 앱 전용이라 넘기면 로그인이 거부된다" {
                val url = client.getAuthorizationUrl()

                url shouldContain "${OAuthConstants.PARAM_SCOPE}=profile_nickname"
            }

            "설정한 동의항목을 콤마로 이어 붙인다 (비즈 앱 전환 시 설정만 늘리면 된다)" {
                val bizClient = KakaoOAuthClient(
                    properties = properties.copy(scopes = listOf("profile_nickname", "account_email", "gender")),
                    client = apiSender,
                )

                val url = bizClient.getAuthorizationUrl()

                url shouldContain "${OAuthConstants.PARAM_SCOPE}=profile_nickname,account_email,gender"
            }

            "동의항목 설정이 비어 있으면 scope 파라미터를 붙이지 않는다 (앱 설정을 그대로 따른다)" {
                val noScopeClient = KakaoOAuthClient(
                    properties = properties.copy(scopes = emptyList()),
                    client = apiSender,
                )

                val url = noScopeClient.getAuthorizationUrl()

                url shouldNotContain OAuthConstants.PARAM_SCOPE
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
                name: String? = null,
                phoneNumber: String? = null,
                gender: String? = null,
            ) = KakaoUserResponse.KakaoAccount(
                profile = KakaoUserResponse.KakaoProfile(nickname = nickname),
                email = email,
                birthyear = birthyear,
                birthday = birthday,
                name = name,
                phoneNumber = phoneNumber,
                gender = gender,
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
                        name = null,
                        phoneNumber = null,
                        gender = null,
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

            "이름이 있으면 해당 값을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(name = "홍길동"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.name shouldBe "홍길동"
            }

            "이름이 없으면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(name = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.name shouldBe null
            }

            "전화번호 +82 국가코드를 010-XXXX-XXXX 포맷으로 변환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = "+82 10-1234-5678"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe "010-1234-5678"
            }

            "전화번호 +82 010 형식도 010-XXXX-XXXX로 변환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = "+82 010-1234-5678"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe "010-1234-5678"
            }

            "전화번호가 국가코드 없는 국내 포맷이면 그대로 010-XXXX-XXXX로 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = "010-9876-5432"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe "010-9876-5432"
            }

            "전화번호가 없으면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe null
            }

            "전화번호가 빈 문자열이면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = "  "),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe null
            }

            "전화번호 자릿수가 비정상이면 예외 없이 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(phoneNumber = "+82 10-123"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.phoneNumber shouldBe null
            }

            "성별 male을 MALE로 변환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(gender = "male"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.gender shouldBe Gender.MALE
            }

            "성별 female을 FEMALE로 변환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(gender = "female"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.gender shouldBe Gender.FEMALE
            }

            "성별이 없으면 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(gender = null),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.gender shouldBe null
            }

            "성별 값을 알 수 없으면 예외 없이 null을 반환한다" {
                every { apiSender.getUserInfo("Bearer test-token") } returns KakaoUserResponse(
                    id = 12345L,
                    kakaoAccount = kakaoAccount(gender = "unknown"),
                )

                val userInfo = client.getUserInfo("test-token")

                userInfo.gender shouldBe null
            }
        }
    },
)
