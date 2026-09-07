package com.ditto.infrastructure.oauth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.apple.AppleIdTokenPayload
import com.ditto.infrastructure.oauth.apple.AppleIdTokenVerifier
import com.ditto.infrastructure.oauth.apple.AppleNativeAuthenticator
import com.ditto.infrastructure.oauth.apple.AppleNativeFakeAuthenticator
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import com.ditto.infrastructure.oauth.kakao.KakaoNativeAuthenticator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate

/**
 * 네이티브 로그인 인증기들. 제공자마다 확인 방법이 달라도(카카오=API 조회, 애플=토큰 검증)
 * 위쪽에는 같은 [OAuthUserInfo]로 보이는지를 본다.
 */
class NativeSocialAuthenticatorTest : FreeSpec(
    {
        "KakaoNativeAuthenticator" - {
            "액세스 토큰으로 사용자 정보 조회를 위임한다" {
                val client = mockk<OAuthClient>()
                val userInfo = OAuthUserInfo(
                    id = "12345",
                    nickname = "카카오유저",
                    email = "user@kakao.com",
                    birthDate = LocalDate.of(1995, 3, 15),
                    name = "김철수",
                    phoneNumber = "010-1234-5678",
                    gender = Gender.MALE,
                )
                every { client.getUserInfo("kakao-access-token") } returns userInfo

                val result = KakaoNativeAuthenticator(client)
                    .authenticate(NativeSocialCredential(token = "kakao-access-token"))

                result shouldBe userInfo
                verify { client.getUserInfo("kakao-access-token") }
            }
        }

        "AppleNativeAuthenticator" - {
            fun authenticatorReturning(payload: AppleIdTokenPayload): AppleNativeAuthenticator {
                val verifier = mockk<AppleIdTokenVerifier>()
                every { verifier.verify(any(), any()) } returns payload
                return AppleNativeAuthenticator(verifier)
            }

            "ID 토큰의 sub 와 이메일을 사용자 정보로 옮긴다" {
                val authenticator = authenticatorReturning(
                    AppleIdTokenPayload(
                        subject = "001234.apple-subject.0000",
                        email = "user@privaterelay.appleid.com",
                        isPrivateEmail = true,
                    ),
                )

                val result = authenticator.authenticate(NativeSocialCredential(token = "apple-id-token"))

                result.id shouldBe "001234.apple-subject.0000"
                result.email shouldBe "user@privaterelay.appleid.com"
                result.nickname shouldBe OAuthConstants.DEFAULT_NICKNAME
            }

            "애플이 주지 않는 값(생년월일·전화번호·성별)은 null 이다 — 온보딩에서 받는다" {
                val authenticator = authenticatorReturning(
                    AppleIdTokenPayload(subject = "sub", email = null, isPrivateEmail = false),
                )

                val result = authenticator.authenticate(NativeSocialCredential(token = "apple-id-token"))

                result.email shouldBe null
                result.birthDate shouldBe null
                result.phoneNumber shouldBe null
                result.gender shouldBe null
            }

            "이름은 앱이 준 값을 그대로 쓴다 — 애플은 최초 인가 1회만 이름을 주고 ID 토큰에는 없다" {
                val authenticator = authenticatorReturning(
                    AppleIdTokenPayload(subject = "sub", email = null, isPrivateEmail = false),
                )

                val firstLogin = authenticator.authenticate(
                    NativeSocialCredential(token = "apple-id-token", name = "김철수"),
                )
                val reLogin = authenticator.authenticate(NativeSocialCredential(token = "apple-id-token"))

                firstLogin.name shouldBe "김철수"
                reLogin.name shouldBe null
            }

            "원본 nonce 를 검증기에 그대로 넘긴다" {
                val verifier = mockk<AppleIdTokenVerifier>()
                every { verifier.verify(any(), any()) } returns
                    AppleIdTokenPayload(subject = "sub", email = null, isPrivateEmail = false)

                AppleNativeAuthenticator(verifier).authenticate(
                    NativeSocialCredential(token = "apple-id-token", rawNonce = "client-nonce"),
                )

                verify { verifier.verify("apple-id-token", "client-nonce") }
            }
        }

        "AppleNativeFakeAuthenticator" - {
            "local·test 에서는 애플에 붙지 않고 고정 사용자를 준다" {
                val result = AppleNativeFakeAuthenticator()
                    .authenticate(NativeSocialCredential(token = "무엇이든", name = "김철수"))

                result.id shouldBe AppleNativeFakeAuthenticator.FAKE_SUBJECT
                result.name shouldBe "김철수"
                result.gender shouldBe null
            }
        }

        "NativeSocialAuthenticatorFactory" - {
            "등록된 제공자의 인증기를 준다" {
                val appleAuthenticator = AppleNativeFakeAuthenticator()
                val factory = NativeSocialAuthenticatorFactory(
                    mapOf(SocialProvider.APPLE to appleAuthenticator),
                )

                factory.getAuthenticator(SocialProvider.APPLE) shouldBe appleAuthenticator
            }

            "등록되지 않은 제공자면 지원하지 않는 제공자로 알린다" {
                val factory = NativeSocialAuthenticatorFactory(emptyMap())

                val exception = shouldThrow<ErrorException> {
                    factory.getAuthenticator(SocialProvider.KAKAO)
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }
    },
)
