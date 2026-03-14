package com.ditto.api.config.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.security")
data class ApiKeyProperties(
    val apiKey: String,
)
