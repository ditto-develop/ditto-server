package com.ditto.api.auth

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
