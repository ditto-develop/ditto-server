package com.ditto.api.notification.dto

/** [registered]는 이번 호출로 내 소유가 됐는지다. 멱등 재호출이면 `false` — 실패 아님(실패는 `success`가 말한다). */
data class MemberDeviceRegisterResponse(
    val registered: Boolean,
)
