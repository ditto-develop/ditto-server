package com.ditto.api.auth.dto

import jakarta.validation.constraints.NotBlank

/**
 * 네이티브 소셜 로그인(앱) 요청 — 네이티브 SDK가 이미 발급받은 소셜 액세스 토큰.
 * 웹 리다이렉트 로그인이 인가 코드를 넘기는 자리에 이 토큰이 들어온다.
 */
data class NativeSocialLoginRequest(
    @field:NotBlank
    val accessToken: String,
)
