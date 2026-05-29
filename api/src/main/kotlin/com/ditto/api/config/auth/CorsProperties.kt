package com.ditto.api.config.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
