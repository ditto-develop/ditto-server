package com.ditto.api.quiz

import com.ditto.api.quiz.service.QuizSetService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.TestClockConfig
import org.springframework.context.annotation.Import
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

@Import(TestClockConfig::class)
class QuizSetServiceTest(
    private val quizSetService: QuizSetService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val fixedTime = TestClockConfig.FIXED_TIME

    "이번 주차 퀴즈 세트 조회" - {
        "활성 퀴즈 세트가 있으면 퀴즈와 선택지를 포함하여 조회한다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = fixedTime.minusDays(1),
                    endDate = fixedTime.plusDays(1),
                ),
            )
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = quizSet.id, question = "짜장면 vs 짬뽕?"),
            )
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "짜장면", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "짬뽕", displayOrder = 2))

            val result = quizSetService.getCurrentWeekQuizSets()

            result.quizSets.size shouldBe 1
            result.quizSets[0].quizzes.size shouldBe 1
            result.quizSets[0].quizzes[0].question shouldBe "짜장면 vs 짬뽕?"
            result.quizSets[0].quizzes[0].choices.size shouldBe 2
        }

        "활성 퀴즈 세트가 없으면 빈 목록을 반환한다" {
            val result = quizSetService.getCurrentWeekQuizSets()

            result.quizSets.size shouldBe 0
        }

        "비활성 퀴즈 세트는 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = fixedTime.minusDays(1),
                    endDate = fixedTime.plusDays(1),
                    isActive = false,
                ),
            )

            val result = quizSetService.getCurrentWeekQuizSets()

            result.quizSets.size shouldBe 0
        }

        "퀴즈가 순서대로 정렬된다" {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = fixedTime.minusDays(1),
                    endDate = fixedTime.plusDays(1),
                ),
            )
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "첫번째", displayOrder = 1))

            val result = quizSetService.getCurrentWeekQuizSets()

            result.quizSets[0].quizzes[0].question shouldBe "첫번째"
            result.quizSets[0].quizzes[1].question shouldBe "두번째"
        }

        "현재 시간 범위 밖의 퀴즈 세트는 조회되지 않는다" {
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = fixedTime.plusDays(1),
                    endDate = fixedTime.plusDays(7),
                ),
            )

            val result = quizSetService.getCurrentWeekQuizSets()

            result.quizSets.size shouldBe 0
        }
    }

    "퀴즈 세트 단건 조회" - {
        "ID로 퀴즈 세트를 조회한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(title = "테스트 퀴즈"))

            val result = quizSetService.getQuizSet(quizSet.id)

            result.id shouldBe quizSet.id
            result.title shouldBe "테스트 퀴즈"
        }

        "존재하지 않는 ID로 조회하면 예외가 발생한다" {
            val exception = shouldThrow<WarnException> {
                quizSetService.getQuizSet(99999L)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
