package com.ditto.api.auth.dto

/**
 * OAuth 로그인 결과 (facade → controller 전달용).
 * - redirectUrl: accessToken·signupRequired 쿼리가 포함된 프론트 콜백 URL
 * - refreshToken: 컨트롤러에서 HttpOnly 쿠키로 전달
 */
data class OAuthLoginResult(
    val redirectUrl: String,
    val refreshToken: String,
)
