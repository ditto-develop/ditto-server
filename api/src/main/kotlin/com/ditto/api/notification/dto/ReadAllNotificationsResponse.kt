package com.ditto.api.notification.dto

/**
 * "모두 읽음"의 결과. [readCount]는 이번 호출로 읽음이 된 건수다 —
 * 이미 다 읽은 상태에서 다시 눌러도 성공이며 0이 온다(멱등).
 */
data class ReadAllNotificationsResponse(
    val readCount: Long,
)
