package com.ditto.api.admin

import com.ditto.api.admin.quiz.AdminQuizService
import com.ditto.api.admin.quiz.dto.QuizSetForm
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminQuizServiceTest(
    private val adminQuizService: AdminQuizService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun quizSetForm(startDate: LocalDateTime, endDate: LocalDateTime) = QuizSetForm(
        category = "성격",
        title = "주간 검증 테스트",
        startDate = startDate,
        endDate = endDate,
    )

    "퀴즈셋 기간 검증" - {
        "기간이 한 운영 주 안이면 생성되고 weekStartedOn이 시작일의 월요일로 파생된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 29, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            quizSet.weekStartedOn shouldBe LocalDate.of(2026, 7, 27)
        }

        "기간이 두 운영 주에 걸치면 생성이 거부된다" {
            val exception = shouldThrow<WarnException> {
                adminQuizService.createQuizSet(
                    quizSetForm(
                        startDate = LocalDateTime.of(2026, 7, 24, 0, 0),
                        endDate = LocalDateTime.of(2026, 7, 28, 23, 59),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "수정 기간이 두 운영 주에 걸치면 수정이 거부된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            shouldThrow<WarnException> {
                adminQuizService.updateQuizSet(
                    quizSet.id,
                    quizSetForm(
                        startDate = LocalDateTime.of(2026, 7, 31, 0, 0),
                        endDate = LocalDateTime.of(2026, 8, 3, 23, 59),
                    ),
                )
            }
        }

        "수정으로 시작일을 다른 주로 옮기면 weekStartedOn이 재파생된다" {
            val quizSet = adminQuizService.createQuizSet(
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 7, 27, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 2, 23, 59),
                ),
            )

            adminQuizService.updateQuizSet(
                quizSet.id,
                quizSetForm(
                    startDate = LocalDateTime.of(2026, 8, 3, 0, 0),
                    endDate = LocalDateTime.of(2026, 8, 9, 23, 59),
                ),
            )

            adminQuizService.getQuizSet(quizSet.id).weekStartedOn shouldBe LocalDate.of(2026, 8, 3)
        }
    }
})
