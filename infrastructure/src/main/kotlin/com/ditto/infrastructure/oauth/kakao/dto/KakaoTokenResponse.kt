package com.ditto.infrastructure.oauth.kakao.dto

data class KakaoTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String? = null,
    val expiresIn: Int,
)
