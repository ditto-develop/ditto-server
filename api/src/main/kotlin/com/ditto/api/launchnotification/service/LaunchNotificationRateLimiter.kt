package com.ditto.api.launchnotification.service

import com.ditto.api.support.ratelimit.FixedWindowRateLimiter
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 출시 알림 신청의 IP당 요청 한도. 로그인 전 호출이고 API Key가 웹 번들에 들어 있어 사실상 공개라서,
 * 남은 방어가 이것뿐이다. IP를 바꿔 가며 보내는 공격은 막지 못한다.
 */
@Component
class LaunchNotificationRateLimiter(
    private val launchNotificationMetrics: LaunchNotificationMetrics,
) {
    private val limiter = FixedWindowRateLimiter(limit = LIMIT_PER_WINDOW, window = WINDOW)

    fun check(clientIp: String) {
        if (!limiter.tryAcquire(clientIp)) {
            launchNotificationMetrics.countRateLimited()
            throw WarnException(ErrorCode.TOO_MANY_REQUESTS)
        }
    }

    companion object {
        const val LIMIT_PER_WINDOW = 5
        val WINDOW: Duration = Duration.ofMinutes(1)
    }
}
