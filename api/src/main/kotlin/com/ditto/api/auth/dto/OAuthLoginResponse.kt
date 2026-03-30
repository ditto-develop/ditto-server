package com.ditto.api.auth.dto

data class OAuthLoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
