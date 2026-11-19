package com.ditto.api.match

import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class GroupMatchingStatusTest : FreeSpec({
    // 2026-11-16(월) 시작 주. 그룹 응답 마감은 금 00:00이다.
    val week = OperationWeek(LocalDate.of(2026, 11, 16))
    val beforeGroupResponseDeadline = LocalDateTime.of(2026, 11, 19, 23, 59, 59)
    val groupResponseDeadline = LocalDateTime.of(2026, 11, 20, 0, 0)

    "상태마다 그룹 응답 마감 전후로 1:1 을 풀 수 있는지가 정해진다" {
        val expectedBeforeAndAfterDeadline = mapOf(
            GroupMatchingStatus.NOT_INVITED to (true to true),
            GroupMatchingStatus.DECLINED to (true to true),
            GroupMatchingStatus.NO_CANDIDATE_GROUPS to (false to true),
            GroupMatchingStatus.NOT_RESPONDED to (false to true),
            GroupMatchingStatus.WAITING_FOR_GROUP to (false to true),
            GroupMatchingStatus.JOINED_GROUP to (false to false),
        )

        expectedBeforeAndAfterDeadline.keys shouldBe GroupMatchingStatus.entries.toSet()
        expectedBeforeAndAfterDeadline.forEach { (status, expected) ->
            val (beforeDeadline, afterDeadline) = expected
            status.canTakeOneToOne(week, beforeGroupResponseDeadline) shouldBe beforeDeadline
            status.canTakeOneToOne(week, groupResponseDeadline) shouldBe afterDeadline
        }
    }
})
