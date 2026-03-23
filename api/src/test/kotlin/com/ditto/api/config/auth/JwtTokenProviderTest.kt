package com.ditto.api.config.auth

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

class JwtTokenProviderTest : FreeSpec(
    {

        val jwtProperties = JwtProperties(
            secret = "test-jwt-secret-key-must-be-at-least-256-bits-long-for-hmac-sha-testing",
            expirationMs = 3600000L,
        )
        val jwtTokenProvider = JwtTokenProvider(jwtProperties)

        "토큰 생성" - {
            "유효한 JWT 문자열을 반환한다" {
                val token = jwtTokenProvider.generateToken(1L)

                token.shouldNotBeBlank()
            }

            "생성된 토큰은 유효하다" {
                val token = jwtTokenProvider.generateToken(1L)

                jwtTokenProvider.isValid(token) shouldBe true
            }
        }

        "토큰에서 memberId 추출" - {
            "올바른 memberId를 추출한다" {
                val memberId = 42L
                val token = jwtTokenProvider.generateToken(memberId)

                jwtTokenProvider.getMemberId(token) shouldBe memberId
            }
        }

        "토큰 검증" - {
            "만료된 토큰은 유효하지 않다" {
                val expiredProvider = JwtTokenProvider(
                    JwtProperties(secret = jwtProperties.secret, expirationMs = 0L),
                )
                val token = expiredProvider.generateToken(1L)

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
                    ),
                )
                val token = otherProvider.generateToken(1L)

                jwtTokenProvider.isValid(token) shouldBe false
            }
        }
    },
)
