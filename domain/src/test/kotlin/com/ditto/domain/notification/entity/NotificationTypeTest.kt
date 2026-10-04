package com.ditto.domain.notification.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NotificationTypeTest : FreeSpec({

    "pointingTo" - {
        "같은 대상을 가리키는 유형만 고른다" {
            NotificationType.pointingTo(NotificationTarget.PERSONAL_MATCH) shouldContainExactlyInAnyOrder listOf(
                NotificationType.MATCH_REQUESTED,
                NotificationType.MATCH_ACCEPTED,
                NotificationType.MATCH_REJECTED,
            )
        }

        "모든 유형은 정확히 한 대상에 속한다" {
            NotificationTarget.entries.sumOf { NotificationType.pointingTo(it).size } shouldBe
                NotificationType.entries.size
        }
    }

    "target 은 사람이 읽는 설명과 같은 테이블을 가리킨다" {
        NotificationType.entries.forEach { type ->
            val describedTable = type.targetDescription.substringBefore(".id")
            describedTable shouldBe type.target.name.lowercase()
        }
    }
})
