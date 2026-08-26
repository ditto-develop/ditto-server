package com.ditto.api.notification.dto

/**
 * 등록 결과. [registered]는 이번 호출로 이 회원 소유가 됐는지다(신규 등록·소유권 이전).
 * 이미 내 토큰이었던 재호출(멱등)이면 `false`다 — 실패가 아니다. 실패는 `success`가 말한다.
 */
data class MemberDeviceRegisterResponse(
    val registered: Boolean,
)
