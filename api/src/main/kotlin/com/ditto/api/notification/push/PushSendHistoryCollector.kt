package com.ditto.api.notification.push

import com.ditto.domain.notification.entity.PushSendHistory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 한 번에 보낸 푸시들의 발송 이력을 모았다가 마지막 응답이 오면 한꺼번에 넘긴다.
 * 응답은 FCM 스레드 여러 개에서 동시에 오므로 스레드 안전한 큐와 카운터로 센다.
 * 응답 하나가 끝내 오지 않으면(서버가 그 사이 꺼지는 등) 그 묶음은 넘기지 못한다.
 */
class PushSendHistoryCollector(
    private val expectedCount: Int,
    private val onComplete: (List<PushSendHistory>) -> Unit,
) {
    private val histories = ConcurrentLinkedQueue<PushSendHistory>()
    private val receivedCount = AtomicInteger()

    fun add(history: PushSendHistory) {
        histories.add(history)
        if (receivedCount.incrementAndGet() == expectedCount) {
            onComplete(histories.toList())
        }
    }
}
