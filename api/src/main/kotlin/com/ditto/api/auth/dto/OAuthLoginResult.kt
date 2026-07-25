package com.ditto.api.auth.dto

/**
 * OAuth 로그인 결과 (facade → controller 전달용).
 * - redirectUrl: accessToken·signupRequired 쿼리가 포함된 프론트 콜백 URL (제재 회원은 sanctioned 쿼리)
 * - refreshToken: 컨트롤러에서 HttpOnly 쿠키로 전달 (제재 회원은 발급하지 않아 null)
 */
data class OAuthLoginResult(
    val redirectUrl: String,
    val refreshToken: String?,
)
