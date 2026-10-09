package com.ditto.api.auth.controller

import com.ditto.api.auth.dto.AppleServerNotificationRequest
import com.ditto.api.auth.service.AppleServerNotificationService
import com.ditto.common.response.ApiResponse
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationVerifier
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 애플 개발자 포털에 등록하는 서버 간 알림 엔드포인트. 애플은 API Key 도 JWT 도 보내지 않는다.
 * payload 에 애플 사용자 식별자와 이메일이 들어 있어 인자를 로그로 남기는 @Loggable 을 붙이지 않는다.
 */
@RestController
class AppleServerNotificationController(
    private val appleServerNotificationVerifier: AppleServerNotificationVerifier,
    private val appleServerNotificationService: AppleServerNotificationService,
) {

    @PostMapping("/api/v1/users/social-login/apple/notifications")
    fun receiveAppleServerNotification(
        @Valid @RequestBody request: AppleServerNotificationRequest,
    ): ApiResponse<Unit> {
        // 공개키를 받아오는 외부 호출이 있어 트랜잭션 밖에서 검증한다.
        val notification = appleServerNotificationVerifier.verify(request.payload)
        appleServerNotificationService.handle(notification)
        return ApiResponse.ok(Unit)
    }
}
