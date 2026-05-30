package com.ditto.infrastructure.oauth.kakao.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val refreshToken: String? = null,
    val expiresIn: Int,
)
