package com.ditto.api.auth.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OAuthLoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)
