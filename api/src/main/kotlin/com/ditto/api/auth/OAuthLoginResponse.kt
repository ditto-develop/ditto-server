package com.ditto.api.auth

data class OAuthLoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
