package com.ditto.api.setting.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.setting.dto.BlockedMemberResponse
import com.ditto.api.setting.dto.CreateBlockRequest
import com.ditto.api.setting.dto.NotificationSettingsResponse
import com.ditto.api.setting.dto.UpdateNotificationSettingsRequest
import com.ditto.api.setting.service.MemberBlockService
import com.ditto.api.setting.service.NotificationSettingService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 설정 화면(피그마 6.2)의 알림 설정·차단 목록 엔드포인트. */
@RestController
class SettingController(
    private val notificationSettingService: NotificationSettingService,
    private val memberBlockService: MemberBlockService,
) {

    @Loggable
    @GetMapping("/api/v1/users/me/notification-settings")
    fun getNotificationSettings(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<NotificationSettingsResponse> =
        ApiResponse.ok(notificationSettingService.getSettings(principal.memberId))

    @Loggable
    @PatchMapping("/api/v1/users/me/notification-settings")
    fun updateNotificationSettings(
        @RequestBody request: UpdateNotificationSettingsRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<NotificationSettingsResponse> =
        ApiResponse.ok(notificationSettingService.updateSettings(principal.memberId, request))

    @Loggable
    @GetMapping("/api/v1/users/me/blocks")
    fun getMyBlocks(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<List<BlockedMemberResponse>> =
        ApiResponse.ok(memberBlockService.getMyBlocks(principal.memberId))

    /**
     * 신고 없이 차단만 하는 경로. 신고와 함께 차단하는 경우는
     * `POST /api/v1/user-reports`의 `block` 필드를 쓴다.
     */
    @Loggable
    @PostMapping("/api/v1/users/me/blocks")
    fun block(
        @Valid @RequestBody request: CreateBlockRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<Unit> {
        memberBlockService.block(principal.memberId, request.memberId)
        return ApiResponse.ok(Unit)
    }

    @Loggable
    @DeleteMapping("/api/v1/users/me/blocks/{memberId}")
    fun unblock(
        @PathVariable memberId: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<Unit> {
        memberBlockService.unblock(principal.memberId, memberId)
        return ApiResponse.ok(Unit)
    }
}
