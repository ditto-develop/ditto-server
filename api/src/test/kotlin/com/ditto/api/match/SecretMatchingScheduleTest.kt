package com.ditto.api.match

import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.system.OperationWeek
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class SecretMatchingScheduleTest : FreeSpec({
    val week = OperationWeek(LocalDate.of(2026, 11, 16))

    "1차 라운드는 금 20:00, 2차 라운드는 토 20:00 이다" {
        SecretMatchingSchedule.roundStartsAt(CandidateOrigin.SECRET_FIRST, week) shouldBe LocalDateTime.of(2026, 11, 20, 20, 0)
        SecretMatchingSchedule.roundStartsAt(CandidateOrigin.SECRET_SECOND, week) shouldBe LocalDateTime.of(2026, 11, 21, 20, 0)
    }

    "늦은 응시는 2차 라운드 시각에 마감된다" {
        SecretMatchingSchedule.lateSubmissionClosesAt(week) shouldBe LocalDateTime.of(2026, 11, 21, 20, 0)
    }

    "지금 돌릴 라운드는 시각이 지난 가장 늦은 라운드다" {
        SecretMatchingSchedule.dueRoundAt(LocalDateTime.of(2026, 11, 20, 19, 59)) shouldBe null
        SecretMatchingSchedule.dueRoundAt(LocalDateTime.of(2026, 11, 20, 20, 1)) shouldBe (week to CandidateOrigin.SECRET_FIRST)
        SecretMatchingSchedule.dueRoundAt(LocalDateTime.of(2026, 11, 21, 20, 1)) shouldBe (week to CandidateOrigin.SECRET_SECOND)
        SecretMatchingSchedule.dueRoundAt(LocalDateTime.of(2026, 11, 22, 23, 59)) shouldBe (week to CandidateOrigin.SECRET_SECOND)
        SecretMatchingSchedule.dueRoundAt(LocalDateTime.of(2026, 11, 23, 20, 1)) shouldBe null
    }

    "다음 라운드는 주어진 시각 뒤의 첫 라운드이고 정각은 다음 라운드로 넘긴다" {
        SecretMatchingSchedule.nextRoundAfter(week, LocalDateTime.of(2026, 11, 19, 0, 0)) shouldBe LocalDateTime.of(2026, 11, 20, 20, 0)
        SecretMatchingSchedule.nextRoundAfter(week, LocalDateTime.of(2026, 11, 20, 20, 0)) shouldBe LocalDateTime.of(2026, 11, 21, 20, 0)
        SecretMatchingSchedule.nextRoundAfter(week, LocalDateTime.of(2026, 11, 21, 20, 0)) shouldBe null
    }

    "정규 매칭은 라운드 시각이 없다" {
        shouldThrow<IllegalArgumentException> { SecretMatchingSchedule.roundStartsAt(CandidateOrigin.REGULAR, week) }
    }
})
