package com.ditto.api.notification.dto

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.NotificationType
import java.time.LocalDateTime

/**
 * 알림 센터 목록의 한 줄.
 *
 * 안읽음은 `readAt == null` 이다 — 불리언을 따로 두지 않는다(스펙 표가 `read_at`이고, 값이 둘이면
 * 어긋날 수 있다). 화면은 안읽음일 때 카드 배경을 `Normal`, 읽음일 때 `Alternative` 로 그린다.
 *
 * 표시 시각은 클라이언트가 [createdAt]으로 만든다("방금 전"·"3시간 전"·"04.08.토") —
 * 상대시간 문구는 지금 몇 시인지에 달려 있어 서버가 미리 만들 수 없다.
 */
data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val category: NotificationCategory,
    val title: String,
    val body: String?,
    /** 눌렀을 때 이동할 대상 ID. 무엇을 가리키는지는 [type]이 정한다. 대상이 없으면 null */
    val targetId: Long?,
    val readAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(notification: Notification): NotificationResponse = NotificationResponse(
            id = notification.id,
            type = notification.type,
            category = notification.category,
            title = notification.title,
            body = notification.body,
            targetId = notification.targetId,
            readAt = notification.readAt,
            createdAt = notification.createdAt,
        )
    }
}
