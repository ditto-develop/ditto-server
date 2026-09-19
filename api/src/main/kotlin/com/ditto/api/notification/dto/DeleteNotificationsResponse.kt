package com.ditto.api.notification.dto

/**
 * 알림 삭제의 결과. [deletedCount]는 이번 호출로 지워진 건수다.
 * 단건 삭제는 항상 1이며(없으면 404), 전체 삭제는 지울 것이 없으면 0이다.
 */
data class DeleteNotificationsResponse(
    val deletedCount: Long,
)
