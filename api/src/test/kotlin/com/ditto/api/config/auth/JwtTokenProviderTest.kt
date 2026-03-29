package com.ditto.api.config.auth

import com.ditto.domain.socialaccount.entity.SocialProvider
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

class JwtTokenProviderTest : FreeSpec(
    {

        val jwtProperties = JwtProperties(
            secret = "test-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha-testing",
            expirationMs = 3600000L,
            refreshExpirationMs = 1209600000L,
        )
        val jwtTokenProvider = JwtTokenProvider(jwtProperties)

        "토큰 생성" - {
            "유효한 JWT 문자열을 반환한다" {
                val token = jwtTokenProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

                token.shouldNotBeBlank()
            }

            "생성된 토큰은 유효하다" {
                val token = jwtTokenProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

                jwtTokenProvider.isValid(token) shouldBe true
            }
        }

        "토큰에서 claim 추출" - {
            "올바른 providerUserId를 추출한다" {
                val providerUserId = "kakao-456"
                val token = jwtTokenProvider.generateAccessToken(providerUserId, SocialProvider.KAKAO)

                jwtTokenProvider.getProviderUserId(token) shouldBe providerUserId
            }

            "올바른 provider를 추출한다" {
                val token = jwtTokenProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

                jwtTokenProvider.getProvider(token) shouldBe SocialProvider.KAKAO
            }
        }

        "토큰 검증" - {
            "만료된 토큰은 유효하지 않다" {
                val expiredProvider = JwtTokenProvider(
                    JwtProperties(secret = jwtProperties.secret, expirationMs = 0L, refreshExpirationMs = 1209600000L),
                )
                val token = expiredProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

                Thread.sleep(10)
                jwtTokenProvider.isValid(token) shouldBe false
            }

            "잘못된 형식의 토큰은 유효하지 않다" {
                jwtTokenProvider.isValid("invalid-token") shouldBe false
            }

            "다른 secret으로 서명된 토큰은 유효하지 않다" {
                val otherProvider = JwtTokenProvider(
                    JwtProperties(
                        secret = "other-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha-testing",
                        expirationMs = 3600000L,
                        refreshExpirationMs = 1209600000L,
                    ),
                )
                val token = otherProvider.generateAccessToken("kakao-123", SocialProvider.KAKAO)

                jwtTokenProvider.isValid(token) shouldBe false
            }
        }
    },
)
