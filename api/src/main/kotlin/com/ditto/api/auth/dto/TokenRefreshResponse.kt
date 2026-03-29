package com.ditto.api.auth.dto

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
