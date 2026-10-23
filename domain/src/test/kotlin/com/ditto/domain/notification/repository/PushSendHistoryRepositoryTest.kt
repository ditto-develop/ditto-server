package com.ditto.domain.notification.repository

import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.entity.PushSkipReason
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class PushSendHistoryRepositoryTest(
    private val pushSendHistoryRepository: PushSendHistoryRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(): PushSendHistory = pushSendHistoryRepository.save(
        PushSendHistory.skipped(NotificationFixture.create(id = 1L), PushSkipReason.NO_DEVICE),
    )

    "deleteCreatedBefore" - {
        "기준 시각 이전에 만들어진 이력을 지운다" {
            save()
            save()

            pushSendHistoryRepository.deleteCreatedBefore(LocalDateTime.now().plusDays(1)) shouldBe 2
            pushSendHistoryRepository.count() shouldBe 0
        }

        "기준 시각 이후의 이력은 남긴다" {
            save()

            pushSendHistoryRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(1)) shouldBe 0
            pushSendHistoryRepository.count() shouldBe 1
        }
    }
})
