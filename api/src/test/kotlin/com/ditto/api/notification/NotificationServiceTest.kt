package com.ditto.api.notification

import com.ditto.api.notification.service.NotificationService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val ME = 1L

class NotificationServiceTest(
    private val notificationService: NotificationService,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveNotifications(count: Int) = (1..count).forEach {
        notificationRepository.save(NotificationFixture.create(memberId = ME, targetId = it.toLong()))
    }

    // 화면이 잘못 보내도 조회가 깨지지 않게 값을 보정한다. 예외를 던지지 않는 쪽을 택했다.
    "size 는 1..100 으로 보정한다" - {
        "0 이하를 보내면 1건만 준다" {
            saveNotifications(3)

            val result = notificationService.getNotifications(ME, category = null, cursor = null, size = 0)

            result.notifications.size shouldBe 1
        }

        "상한을 넘겨 보내도 100건까지만 준다" {
            saveNotifications(101)

            val result = notificationService.getNotifications(ME, category = null, cursor = null, size = 200)

            result.notifications.size shouldBe 100
            // 정확히 채운 페이지라 다음 커서를 준다 — 남은 1건을 이어서 받을 수 있다.
            result.nextCursor shouldBe result.notifications.last().id
        }
    }
})
