package com.ditto.api.notification.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.dto.NotificationsResponse
import com.ditto.api.notification.dto.ReadAllNotificationsResponse
import com.ditto.api.notification.dto.UnreadNotificationCountResponse
import com.ditto.api.notification.service.NotificationService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import com.ditto.domain.notification.entity.NotificationCategory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 알림 센터 화면(피그마 7.2)의 목록·읽음 엔드포인트. */
@RestController
class NotificationController(
    private val notificationService: NotificationService,
) {

    /**
     * 알림 목록(최신순). 상단 필터 칩이 [category]로 온다 — 생략하면 "전체"다.
     * 보관 기간(30일)이 지난 알림은 조회되지 않는다.
     */
    @GetMapping("/api/v1/notifications")
    fun getNotifications(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestParam(required = false) category: NotificationCategory?,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): ApiResponse<NotificationsResponse> =
        ApiResponse.ok(notificationService.getNotifications(principal.memberId, category, cursor, size))

    /** 홈 헤더 벨 배지용 미읽음 수. 목록을 받지 않고 숫자만 필요할 때 쓴다. */
    @GetMapping("/api/v1/notifications/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<UnreadNotificationCountResponse> =
        ApiResponse.ok(UnreadNotificationCountResponse(notificationService.getUnreadCount(principal.memberId)))

    /**
     * 전체 읽음 — 화면 우상단 "모두 읽음". 이미 다 읽었어도 성공하며 `readCount`가 0이다.
     *
     * 개별 읽음(`/{id}/read`)과 세그먼트 수가 달라 경로가 겹치지 않는다.
     */
    @Loggable
    @PutMapping("/api/v1/notifications/read-all")
    fun readAll(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<ReadAllNotificationsResponse> =
        ApiResponse.ok(ReadAllNotificationsResponse(notificationService.markAllRead(principal.memberId)))

    /** 개별 읽음. 이미 읽은 알림에 다시 요청해도 성공한다(멱등). */
    @Loggable
    @PutMapping("/api/v1/notifications/{id}/read")
    fun read(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        notificationService.markRead(principal.memberId, id)
        return ApiResponse.ok(Unit)
    }
}
