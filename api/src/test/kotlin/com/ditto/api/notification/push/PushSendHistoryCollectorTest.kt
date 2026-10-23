package com.ditto.api.notification.push

import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.PushSendHistory
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PushSendHistoryCollectorTest : FreeSpec({

    fun history(id: Long) = PushSendHistory.sent(NotificationFixture.create(id = id), 1, emptyList())

    "기다리던 수가 다 모여야 한 번 넘긴다" {
        var delivered: List<PushSendHistory>? = null
        val collector = PushSendHistoryCollector(2) { delivered = it }

        collector.add(history(1L))
        delivered shouldBe null
        collector.add(history(2L))

        delivered?.map { it.notificationId }?.sorted() shouldBe listOf(1L, 2L)
    }

    "여러 스레드에서 동시에 더해도 한 번만 넘기고 빠지는 이력이 없다" {
        val completedCount = AtomicInteger()
        var delivered: List<PushSendHistory> = emptyList()
        val collector = PushSendHistoryCollector(100) {
            completedCount.incrementAndGet()
            delivered = it
        }
        val executor = Executors.newFixedThreadPool(8)

        (1L..100L).forEach { id -> executor.submit { collector.add(history(id)) } }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        completedCount.get() shouldBe 1
        delivered.size shouldBe 100
    }
})
