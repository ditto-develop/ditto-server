package com.ditto.api.match

import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class GroupResponseDeadlineTest : FreeSpec({
    // 2026-06-01(월) 시작 주. 마감은 2026-06-05(금) 00:00.
    val thisWeek = OperationWeek(LocalDate.of(2026, 6, 1))
    val lastWeek = OperationWeek(LocalDate.of(2026, 5, 25))
    val deadline = LocalDateTime.of(2026, 6, 5, 0, 0)

    "hasPassed" - {
        "마감 1분 전에는 지나지 않았다" {
            GroupResponseDeadline.hasPassed(thisWeek, deadline.minusMinutes(1)) shouldBe false
        }

        "마감 정각부터 지났다" {
            GroupResponseDeadline.hasPassed(thisWeek, deadline) shouldBe true
        }

        "다음 주 월요일에도 그 주 마감은 지난 상태다" {
            GroupResponseDeadline.hasPassed(thisWeek, LocalDateTime.of(2026, 6, 8, 0, 0)) shouldBe true
        }
    }

    "latestClosedWeek" - {
        "이번 주 마감 전이면 지난 주다" {
            GroupResponseDeadline.latestClosedWeek(deadline.minusMinutes(1)) shouldBe lastWeek
        }

        "이번 주 마감 정각부터 이번 주다" {
            GroupResponseDeadline.latestClosedWeek(deadline) shouldBe thisWeek
        }

        "일요일 23:59까지 이번 주다" {
            GroupResponseDeadline.latestClosedWeek(LocalDateTime.of(2026, 6, 7, 23, 59)) shouldBe thisWeek
        }

        "다음 주 월요일 00:00에도 다음 주 마감 전이라 이번 주다" {
            GroupResponseDeadline.latestClosedWeek(LocalDateTime.of(2026, 6, 8, 0, 0)) shouldBe thisWeek
        }
    }
})
