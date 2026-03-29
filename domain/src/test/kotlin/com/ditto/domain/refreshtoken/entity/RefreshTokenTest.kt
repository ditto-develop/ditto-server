package com.ditto.domain.refreshtoken.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class RefreshTokenTest : FreeSpec(
    {
        "RefreshToken 생성" - {
            "create로 RefreshToken을 생성할 수 있다" {
                val refreshToken = RefreshToken.create(
                    memberId = 1L,
                    token = "test-token",
                    expiresAt = LocalDateTime.of(2026, 4, 12, 0, 0),
                )

                refreshToken.memberId shouldBe 1L
                refreshToken.token shouldBe "test-token"
                refreshToken.expiresAt shouldBe LocalDateTime.of(2026, 4, 12, 0, 0)
            }
        }

        "RefreshToken 만료 확인" - {
            "만료 시간이 지났으면 isExpired는 true를 반환한다" {
                val refreshToken = RefreshToken.create(
                    memberId = 1L,
                    token = "expired-token",
                    expiresAt = LocalDateTime.of(2026, 3, 1, 0, 0),
                )

                refreshToken.isExpired(LocalDateTime.of(2026, 3, 2, 0, 0)) shouldBe true
            }

            "만료 시간이 지나지 않았으면 isExpired는 false를 반환한다" {
                val refreshToken = RefreshToken.create(
                    memberId = 1L,
                    token = "valid-token",
                    expiresAt = LocalDateTime.of(2026, 4, 12, 0, 0),
                )

                refreshToken.isExpired(LocalDateTime.of(2026, 3, 29, 0, 0)) shouldBe false
            }

            "만료 시간과 정확히 같으면 만료되지 않은 것으로 판단한다" {
                val expiresAt = LocalDateTime.of(2026, 4, 1, 0, 0)
                val refreshToken = RefreshToken.create(
                    memberId = 1L,
                    token = "test-token",
                    expiresAt = expiresAt,
                )

                refreshToken.isExpired(expiresAt) shouldBe false
            }
        }
    },
)
