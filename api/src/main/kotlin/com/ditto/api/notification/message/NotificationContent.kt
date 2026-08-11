package com.ditto.api.notification.message

import com.ditto.domain.notification.entity.NotificationType

/**
 * 적재할 알림 한 건의 내용 — 유형과 그 유형의 확정된 문구.
 *
 * 유형과 문구를 함께 묶는 이유는 어긋남을 막기 위한 것이다. 부르는 쪽이 유형과 문구를 따로 넘기면
 * `CHAT_MESSAGE` 유형에 재매칭 문구를 붙이는 조합을 만들 수 있고, 문구가 여섯 적재 지점에 흩어진다.
 * 문구는 [NotificationMessages]만 만든다.
 */
data class NotificationContent(
    val type: NotificationType,
    val title: String,
    val body: String?,
)
