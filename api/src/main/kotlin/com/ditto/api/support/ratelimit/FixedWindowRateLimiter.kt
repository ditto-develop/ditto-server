package com.ditto.api.support.ratelimit

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 키(예: 클라이언트 IP)별로 [window] 안에 [limit]회까지만 허용하는 고정 윈도우 리미터.
 *
 * 인메모리라 태스크가 하나일 때만 정확하다 — 지금 ECS 서비스는 태스크 1개다(채팅 SimpleBroker와 같은 전제).
 * 레플리카를 늘리면 키별 한도가 레플리카 수만큼 늘어난다.
 */
class FixedWindowRateLimiter(
    private val limit: Int,
    private val window: Duration,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Window(val startedAt: Long, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** 이번 요청이 한도 안이면 true. 한도를 넘긴 요청도 횟수에 들어간다(계속 두드리면 계속 막힌다). */
    fun tryAcquire(key: String): Boolean {
        val now = nowMillis()
        evictExpiredIfCrowded(now)
        val current = windows.compute(key) { _, previous ->
            if (previous == null || isExpired(previous, now)) Window(now, 1) else previous.copy(count = previous.count + 1)
        }!!
        return current.count <= limit
    }

    // 키가 IP라 무한히 늘 수 있다. 많아졌을 때만 만료 윈도우를 훑어 지운다.
    private fun evictExpiredIfCrowded(now: Long) {
        if (windows.size < EVICTION_THRESHOLD) return
        windows.entries.removeIf { isExpired(it.value, now) }
    }

    private fun isExpired(window: Window, now: Long): Boolean = now - window.startedAt >= this.window.toMillis()

    companion object {
        private const val EVICTION_THRESHOLD = 10_000
    }
}
