package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.QuizResponsePeriod
import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class QuizResponsePeriodTest : FreeSpec(
    {
        "응답 기간" - {
            "그 주 월요일 00:00:00에 시작해 수요일 23:59:59에 끝난다" {
                val period = QuizResponsePeriod(OperationWeek(LocalDate.of(2026, 9, 14)))

                period.startsAt shouldBe LocalDateTime.of(2026, 9, 14, 0, 0, 0)
                period.endsAt shouldBe LocalDateTime.of(2026, 9, 16, 23, 59, 59)
            }

            "수요일이 다음 달로 넘어가는 주(2026-06-29)도 같은 규칙으로 7월 1일에 끝난다" {
                val period = QuizResponsePeriod(OperationWeek(LocalDate.of(2026, 6, 29)))

                period.endsAt shouldBe LocalDateTime.of(2026, 7, 1, 23, 59, 59)
            }

            "종료는 목요일 05:00 매칭 배치 시각보다 앞선다" {
                val period = QuizResponsePeriod(OperationWeek(LocalDate.of(2026, 9, 14)))
                val matchingBatchAt = LocalDateTime.of(2026, 9, 17, 5, 0)

                (period.endsAt < matchingBatchAt) shouldBe true
            }
        }
    },
)
