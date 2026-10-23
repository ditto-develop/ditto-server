package com.ditto.domain.notification.repository.querydsl

import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.entity.PushSkipReason

data class PushSendHistoryCount(
    val notificationType: NotificationType,
    val skipReason: PushSkipReason?,
    val failedDeviceCount: Int,
    val count: Long,
)
