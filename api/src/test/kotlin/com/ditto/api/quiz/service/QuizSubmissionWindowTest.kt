package com.ditto.api.quiz.service

import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class QuizSubmissionWindowTest : FreeSpec({
    // 2026-11-16(월) 시작 주. 응답 마감은 수 23:59:59, 늦은 응시 마감은 토 20:00.
    val monday = LocalDate.of(2026, 11, 16)
    val responseDeadline = monday.plusDays(2).atTime(23, 59, 59)
    val saturdayRound = monday.plusDays(5).atTime(20, 0)

    fun quizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE, isActive: Boolean = true) =
        QuizSetFixture.create(
            startDate = monday.atStartOfDay(),
            endDate = responseDeadline,
            matchingType = matchingType,
            isActive = isActive,
        )

    fun windowAt(now: LocalDateTime, matchingType: MatchingType = MatchingType.ONE_TO_ONE, enabled: Boolean = true) =
        QuizSubmissionWindow.of(quizSet(matchingType), now, lateSubmissionEnabled = enabled)

    "1:1 셋, 늦은 응시 켜짐" - {
        "응답 마감 시각까지는 정규 기간이다" {
            windowAt(monday.atStartOfDay()) shouldBe QuizSubmissionWindow.REGULAR
            windowAt(responseDeadline) shouldBe QuizSubmissionWindow.REGULAR
        }

        "응답 마감 직후부터 토 20:00 전까지 늦은 응시다" {
            windowAt(responseDeadline.plusNanos(1_000)) shouldBe QuizSubmissionWindow.LATE
            windowAt(monday.plusDays(3).atStartOfDay()) shouldBe QuizSubmissionWindow.LATE
            windowAt(saturdayRound.minusSeconds(1)) shouldBe QuizSubmissionWindow.LATE
        }

        "토 20:00 부터 일요일 끝까지 늦은 응시가 마감된다" {
            windowAt(saturdayRound) shouldBe QuizSubmissionWindow.LATE_CLOSED
            windowAt(monday.plusDays(6).atTime(23, 59, 59)) shouldBe QuizSubmissionWindow.LATE_CLOSED
        }

        "다음 주 월요일과 시작 전은 응시 기간이 아니다" {
            windowAt(monday.plusWeeks(1).atStartOfDay()) shouldBe QuizSubmissionWindow.OUTSIDE
            windowAt(monday.atStartOfDay().minusSeconds(1)) shouldBe QuizSubmissionWindow.OUTSIDE
        }
    }

    "늦은 응시가 없는 경우" - {
        "늦은 응시가 꺼져 있으면 마감 뒤는 응시 기간이 아니다" {
            windowAt(monday.plusDays(3).atStartOfDay(), enabled = false) shouldBe QuizSubmissionWindow.OUTSIDE
            windowAt(saturdayRound, enabled = false) shouldBe QuizSubmissionWindow.OUTSIDE
        }

        "그룹 셋은 마감 뒤에 늦은 응시를 받지 않는다" {
            windowAt(monday.plusDays(3).atStartOfDay(), MatchingType.GROUP) shouldBe QuizSubmissionWindow.OUTSIDE
        }

        "비활성 셋은 응답 기간에도 응시 기간이 아니다" {
            QuizSubmissionWindow.of(
                quizSet(isActive = false),
                monday.atStartOfDay(),
                lateSubmissionEnabled = true,
            ) shouldBe QuizSubmissionWindow.OUTSIDE
        }
    }
})
