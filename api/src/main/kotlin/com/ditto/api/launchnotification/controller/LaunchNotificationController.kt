package com.ditto.api.launchnotification.controller

import com.ditto.api.launchnotification.dto.LaunchNotificationSignupRequest
import com.ditto.api.launchnotification.service.LaunchNotificationRateLimiter
import com.ditto.api.launchnotification.service.LaunchNotificationService
import com.ditto.api.support.ratelimit.ClientIp
import com.ditto.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 인자에 방문자 이메일이 있어 인자를 로그로 남기는 @Loggable 을 붙이지 않는다. */
@RestController
class LaunchNotificationController(
    private val launchNotificationService: LaunchNotificationService,
    private val launchNotificationRateLimiter: LaunchNotificationRateLimiter,
) {

    @PostMapping("/api/v1/launch-notification-requests")
    fun requestLaunchNotification(
        @Valid @RequestBody request: LaunchNotificationSignupRequest,
        httpRequest: HttpServletRequest,
    ): ApiResponse<Unit> {
        launchNotificationRateLimiter.check(ClientIp.of(httpRequest))
        launchNotificationService.request(request)
        return ApiResponse(success = true)
    }
}
