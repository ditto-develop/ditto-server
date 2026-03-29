package com.ditto.api.auth

import jakarta.validation.constraints.NotBlank

data class TokenRefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)
