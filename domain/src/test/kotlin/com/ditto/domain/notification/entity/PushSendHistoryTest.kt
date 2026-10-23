package com.ditto.domain.notification.entity

import com.ditto.domain.notification.NotificationFixture
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PushSendHistoryTest : FreeSpec({

    val notification = NotificationFixture.create(memberId = 7L, type = NotificationType.CHAT_MESSAGE, id = 31L)

    "sent" - {
        "알림의 id·회원·유형과 기기 수를 옮겨 적고 건너뛴 이유는 비운다" {
            val history = PushSendHistory.sent(notification, deviceCount = 2, failureCodes = emptyList())

            history.notificationId shouldBe 31L
            history.memberId shouldBe 7L
            history.notificationType shouldBe NotificationType.CHAT_MESSAGE
            history.skipReason shouldBe null
            history.deviceCount shouldBe 2
            history.failedDeviceCount shouldBe 0
            history.failureCodes shouldBe null
        }

        "실패 기기 수는 실패 코드 수고 코드는 겹치지 않게 이어 남긴다" {
            val history = PushSendHistory.sent(
                notification,
                deviceCount = 3,
                failureCodes = listOf("UNAVAILABLE", "UNAVAILABLE", "UNREGISTERED"),
            )

            history.failedDeviceCount shouldBe 3
            history.failureCodes shouldBe "UNAVAILABLE,UNREGISTERED"
        }
    }

    "skipped" - {
        "건너뛴 이유를 남기고 보낸 기기 수는 0 이다" {
            val history = PushSendHistory.skipped(notification, PushSkipReason.TOGGLE_OFF)

            history.skipReason shouldBe PushSkipReason.TOGGLE_OFF
            history.deviceCount shouldBe 0
        }
    }
})
