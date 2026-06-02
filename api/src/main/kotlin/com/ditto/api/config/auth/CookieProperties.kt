package com.ditto.api.config.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.auth.cookie")
data class CookieProperties(
    val secure: Boolean = false,
    val sameSite: String = "Lax",
    val path: String = "/api/v1/users/auth",
)
