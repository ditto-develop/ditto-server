package com.ditto.api.auth.dto

data class OAuthLoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)
