package com.ditto.api.auth.dto

import java.time.LocalDateTime

/**
 * 네이티브 소셜 로그인(앱) 응답. 리다이렉트 콜백이 쿼리 파라미터로 주던 정보를 그대로 JSON으로 옮긴 것이라
 * 분기 의미는 콜백 계약(`docs/domains/auth.md`)과 같다.
 *
 * - 정상: [accessToken] + [signupRequired], `sanctioned=false`
 * - 제재: [accessToken]은 null이고 `sanctioned=true` + [sanctionCode](정지면 [suspendedUntil]까지)
 *
 * refreshToken은 이 본문이 아니라 리다이렉트 로그인과 동일하게 HttpOnly 쿠키로 내려간다.
 */
data class NativeSocialLoginResponse(
    val accessToken: String?,
    val signupRequired: Boolean,
    val sanctioned: Boolean,
    val sanctionCode: String? = null,
    val suspendedUntil: LocalDateTime? = null,
)
