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

    "정규 매칭은 라운드 시각이 없다" {
        shouldThrow<IllegalArgumentException> { SecretMatchingSchedule.roundStartsAt(CandidateOrigin.REGULAR, week) }
    }
})
