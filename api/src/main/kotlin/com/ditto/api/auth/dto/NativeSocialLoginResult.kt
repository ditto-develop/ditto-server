package com.ditto.api.auth.dto

/**
 * 네이티브 소셜 로그인 결과 (facade → controller 전달용).
 * refreshToken을 본문이 아닌 쿠키로 내려야 해서 응답 본문과 분리해 담는다.
 * 제재 회원은 토큰을 발급하지 않으므로 [refreshToken]이 null이다.
 */
data class NativeSocialLoginResult(
    val response: NativeSocialLoginResponse,
    val refreshToken: String?,
)
