package com.ditto.api.auth.dto

/**
 * 토큰 갱신 결과 (서비스 계층 내부 전달용).
 * - accessToken: 응답 바디로 전달
 * - refreshToken: 컨트롤러에서 HttpOnly 쿠키로 재발급
 */
data class TokenRefreshResult(
    val accessToken: String,
    val refreshToken: String,
)
