package com.ditto.domain.refreshtoken.repository

import com.ditto.domain.refreshtoken.RefreshTokenFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class RefreshTokenRepositoryTest(
    private val refreshTokenRepository: RefreshTokenRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "deleteAllByMemberId" - {
        "해당 memberId의 토큰이 모두 삭제된다" {
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 1L, token = "token-1"))
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 1L, token = "token-2"))
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 1L, token = "token-3"))

            refreshTokenRepository.deleteAllByMemberId(1L)

            refreshTokenRepository.findAll().size shouldBe 0
        }

        "다른 memberId의 토큰은 삭제되지 않는다" {
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 1L, token = "token-1"))
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 2L, token = "token-2"))

            refreshTokenRepository.deleteAllByMemberId(1L)

            val remaining = refreshTokenRepository.findAll()
            remaining.size shouldBe 1
            remaining[0].memberId shouldBe 2L
        }

        "해당 memberId의 토큰이 없어도 예외가 발생하지 않는다" {
            refreshTokenRepository.deleteAllByMemberId(99999L)

            refreshTokenRepository.findAll().size shouldBe 0
        }
    }

    "deleteExpiredByMemberId" - {
        val now = LocalDateTime.of(2026, 9, 29, 12, 0)

        "해당 memberId의 만료된 토큰만 삭제된다" {
            refreshTokenRepository.save(
                RefreshTokenFixture.create(memberId = 1L, token = "expired", expiresAt = now.minusSeconds(1)),
            )
            refreshTokenRepository.save(RefreshTokenFixture.create(memberId = 1L, token = "valid", expiresAt = now))

            refreshTokenRepository.deleteExpiredByMemberId(1L, now)

            refreshTokenRepository.findAll().map { it.token } shouldBe listOf("valid")
        }

        "다른 memberId의 만료된 토큰은 삭제되지 않는다" {
            refreshTokenRepository.save(
                RefreshTokenFixture.create(memberId = 2L, token = "others-expired", expiresAt = now.minusDays(1)),
            )

            refreshTokenRepository.deleteExpiredByMemberId(1L, now)

            refreshTokenRepository.findAll().map { it.token } shouldBe listOf("others-expired")
        }
    }
})
