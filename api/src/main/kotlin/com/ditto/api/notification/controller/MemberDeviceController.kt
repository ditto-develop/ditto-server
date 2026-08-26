package com.ditto.api.notification.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.dto.MemberDeviceRegisterRequest
import com.ditto.api.notification.dto.MemberDeviceRegisterResponse
import com.ditto.api.notification.facade.MemberDeviceFacade
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 푸시 디바이스 토큰 등록·해제. 앱 전용이며 웹에서는 부르지 않는다. */
@RestController
class MemberDeviceController(
    private val memberDeviceFacade: MemberDeviceFacade,
) {

    /** 로그인 직후·앱 실행·토큰 갱신 때마다 온다. 재호출해도 행이 늘지 않는다. */
    @Loggable
    @PostMapping("/api/v1/notifications/devices")
    fun register(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Valid @RequestBody request: MemberDeviceRegisterRequest,
    ): ApiResponse<MemberDeviceRegisterResponse> {
        val registered = memberDeviceFacade.register(principal.memberId, request.token, request.platform)
        return ApiResponse.ok(MemberDeviceRegisterResponse(registered))
    }

    /**
     * 로그아웃·탈퇴 직전에 온다. 이미 없는 토큰이어도 성공한다(멱등).
     * `@Loggable` 없음 — 경로 변수 토큰이 raw String 이라 마스킹이 안 돼 로그에 평문으로 남는다.
     */
    @DeleteMapping("/api/v1/notifications/devices/{token}")
    fun unregister(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable token: String,
    ): ApiResponse<Unit> {
        memberDeviceFacade.unregister(principal.memberId, token)
        return ApiResponse.ok(Unit)
    }
}
