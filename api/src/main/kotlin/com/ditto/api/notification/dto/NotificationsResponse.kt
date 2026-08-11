package com.ditto.api.notification.dto

/**
 * 알림 목록 한 페이지. [nextCursor]가 null 이면 마지막 페이지다 —
 * 다음 요청의 `cursor`로 그대로 넣는다(채팅 메시지 페이징과 같은 규칙).
 */
data class NotificationsResponse(
    val notifications: List<NotificationResponse>,
    val nextCursor: Long?,
)
