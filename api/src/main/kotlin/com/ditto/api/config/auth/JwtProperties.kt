package com.ditto.api.config.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMs: Long,
    val refreshExpirationMs: Long,
)
