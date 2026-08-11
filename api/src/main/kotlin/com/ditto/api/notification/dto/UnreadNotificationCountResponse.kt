package com.ditto.api.notification.dto

/** 홈 헤더 벨 배지용. 보관 기간(30일) 안의 안읽은 알림 수만 센다 — 목록에 없는 알림을 배지가 세면 0으로 만들 수 없다. */
data class UnreadNotificationCountResponse(
    val count: Long,
)
