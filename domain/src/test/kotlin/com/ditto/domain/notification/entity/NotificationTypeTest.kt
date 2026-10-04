package com.ditto.domain.notification.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class NotificationTypeTest : FreeSpec({

    "pointingTo" - {
        "같은 대상을 가리키는 유형만 고른다" {
            NotificationType.pointingTo(NotificationTarget.PERSONAL_MATCH) shouldContainExactlyInAnyOrder listOf(
                NotificationType.MATCH_REQUESTED,
                NotificationType.MATCH_ACCEPTED,
                NotificationType.MATCH_REJECTED,
            )
        }
    }
})
