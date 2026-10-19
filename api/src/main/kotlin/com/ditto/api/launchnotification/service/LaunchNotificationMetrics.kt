package com.ditto.api.launchnotification.service

import com.ditto.domain.launchnotification.entity.LaunchPlatform
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 출시 알림 신청 추이와 남용(레이트 리밋 거절)을 Grafana에서 보기 위한 카운터. 이메일은 싣지 않는다.
 */
@Component
class LaunchNotificationMetrics(
    private val meterRegistry: MeterRegistry,
) {

    init {
        // 처음 생긴 시계열의 첫 1은 increase()가 못 잡는다. 조합마다 0으로 미리 만든다.
        LaunchPlatform.entries.forEach { platform ->
            listOf(RESULT_CREATED, RESULT_REFRESHED).forEach { counter(platform.name, it) }
        }
        counter(UNKNOWN_PLATFORM, RESULT_RATE_LIMITED)
    }

    fun countRequested(platform: LaunchPlatform, created: Boolean) =
        counter(platform.name, if (created) RESULT_CREATED else RESULT_REFRESHED).increment()

    // 리미터는 본문을 읽기 전에 거절해서 플랫폼을 모른다.
    fun countRateLimited() = counter(UNKNOWN_PLATFORM, RESULT_RATE_LIMITED).increment()

    private fun counter(platform: String, result: String): Counter =
        Counter.builder(METRIC)
            .description("출시 알림 신청 요청 결과")
            .tag("platform", platform)
            .tag("result", result)
            .register(meterRegistry)

    companion object {
        const val METRIC = "ditto.launch.notification.requests"
        const val RESULT_CREATED = "created"
        const val RESULT_REFRESHED = "refreshed"
        const val RESULT_RATE_LIMITED = "rate_limited"
        private const val UNKNOWN_PLATFORM = "unknown"
    }
}
