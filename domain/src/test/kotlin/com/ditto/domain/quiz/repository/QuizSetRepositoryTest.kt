package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizSetRepositoryTest(
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.of(2026, 4, 10, 12, 0)

    "findCurrentWeekActive" - {
        "현재 시간이 startDate~endDate 범위 안이고 활성이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "비활성 퀴즈 세트는 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "현재 시간이 startDate 이전이면 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.plusDays(1), endDate = now.plusDays(7), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "현재 시간이 endDate 이후이면 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now.minusDays(1), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 0
        }

        "경계값 - startDate와 같은 시간이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now, endDate = now.plusDays(7), isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "경계값 - endDate와 같은 시간이면 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now, isActive = true),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 1
        }

        "여러 활성 퀴즈 세트가 있으면 모두 조회된다" {
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = now.minusDays(1), endDate = now.plusDays(1),
                    matchingType = MatchingType.ONE_TO_ONE, category = "성격",
                ),
            )
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = now.minusDays(1), endDate = now.plusDays(1),
                    matchingType = MatchingType.GROUP, category = "취미",
                ),
            )

            val result = quizSetRepository.findCurrentWeekActive(now)

            result.size shouldBe 2
        }
    }
})
