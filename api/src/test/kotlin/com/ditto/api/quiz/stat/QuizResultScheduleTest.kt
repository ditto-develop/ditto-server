package com.ditto.api.quiz.stat

import com.ditto.domain.quiz.QuizSetFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class QuizResultScheduleTest : FreeSpec({

    "결과는 마감 뒤 첫 자정에 공개된다 — 수 23:59:59 마감이면 목 00:00" {
        val quizSet = QuizSetFixture.create(
            startDate = LocalDateTime.of(2026, 11, 2, 0, 0),
            endDate = LocalDateTime.of(2026, 11, 4, 23, 59, 59),
        )

        QuizResultSchedule.opensAt(quizSet) shouldBe LocalDateTime.of(2026, 11, 5, 0, 0)
        QuizResultSchedule.isOpen(quizSet, LocalDateTime.of(2026, 11, 4, 23, 59, 59)) shouldBe false
        QuizResultSchedule.isOpen(quizSet, LocalDateTime.of(2026, 11, 5, 0, 0)) shouldBe true
    }

    "마감이 정확히 자정이면 그 시각에 공개된다" {
        val quizSet = QuizSetFixture.create(
            startDate = LocalDateTime.of(2026, 11, 2, 0, 0),
            endDate = LocalDateTime.of(2026, 11, 5, 0, 0),
        )

        QuizResultSchedule.opensAt(quizSet) shouldBe LocalDateTime.of(2026, 11, 5, 0, 0)
    }
})
