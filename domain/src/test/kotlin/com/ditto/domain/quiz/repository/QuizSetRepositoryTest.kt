package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizSetRepositoryTest(
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
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

    "findLatestCompletedQuizSet" - {
        fun saveCompletedProgress(memberId: Long, quizSetId: Long) {
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
            progress.recordAnswer() // NOT_STARTED -> COMPLETED
            quizProgressRepository.save(progress)
        }

        "완료한 해당 타입 퀴즈셋이 여러 개면 endDate 가 가장 최근인 것을 반환한다" {
            val older = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(14), endDate = now.minusDays(8)),
            )
            val latest = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now.minusDays(1)),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = older.id)
            saveCompletedProgress(memberId = 1L, quizSetId = latest.id)

            val result = quizSetRepository.findLatestCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)

            result?.id shouldBe latest.id
        }

        "완료(COMPLETED)하지 않은 진행 기록만 있으면 제외된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(endDate = now.minusDays(1)))
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id, totalCount = 5),
            )

            val result = quizSetRepository.findLatestCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)

            result shouldBe null
        }

        "요청한 매칭 타입과 다른 퀴즈셋은 제외된다" {
            val groupSet = quizSetRepository.save(
                QuizSetFixture.create(endDate = now.minusDays(1), matchingType = MatchingType.GROUP),
            )
            saveCompletedProgress(memberId = 1L, quizSetId = groupSet.id)

            val result = quizSetRepository.findLatestCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)

            result shouldBe null
        }

        "다른 회원의 완료 기록은 제외된다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(endDate = now.minusDays(1)))
            saveCompletedProgress(memberId = 2L, quizSetId = quizSet.id)

            val result = quizSetRepository.findLatestCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)

            result shouldBe null
        }

        "완료한 퀴즈셋이 없으면 null 을 반환한다" {
            val result = quizSetRepository.findLatestCompletedQuizSet(1L, MatchingType.ONE_TO_ONE)

            result shouldBe null
        }
    }
})
