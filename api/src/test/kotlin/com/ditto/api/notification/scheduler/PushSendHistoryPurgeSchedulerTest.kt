package com.ditto.api.notification.scheduler

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.entity.PushSkipReason
import com.ditto.domain.notification.repository.PushSendHistoryRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class PushSendHistoryPurgeSchedulerTest(
    private val pushSendHistoryPurgeScheduler: PushSendHistoryPurgeScheduler,
    private val pushSendHistoryRepository: PushSendHistoryRepository,
    private val dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun save(): PushSendHistory = pushSendHistoryRepository.save(
        PushSendHistory.skipped(NotificationFixture.create(id = 1L), PushSkipReason.NO_DEVICE),
    )

    fun moveCreatedAt(history: PushSendHistory, createdAt: LocalDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE push_send_history SET created_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, createdAt)
                statement.setLong(2, history.id)
                statement.executeUpdate()
            }
        }
    }

    "purgeExpired" - {
        "180일이 지난 이력만 지운다" {
            moveCreatedAt(save(), LocalDateTime.now().minusDays(PushSendHistory.RETENTION_DAYS + 1))
            val recent = save()

            pushSendHistoryPurgeScheduler.purgeExpired()

            pushSendHistoryRepository.findAll().map { it.id } shouldBe listOf(recent.id)
        }
    }
})
