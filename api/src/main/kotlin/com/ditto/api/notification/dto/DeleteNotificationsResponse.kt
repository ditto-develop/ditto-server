package com.ditto.api.notification.dto

/** 삭제 결과. 단건은 항상 1이고(없으면 404), 전체는 지울 것이 없으면 0이다. */
data class DeleteNotificationsResponse(
    val deletedCount: Long,
)
