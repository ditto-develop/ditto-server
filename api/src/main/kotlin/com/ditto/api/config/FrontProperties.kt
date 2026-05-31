package com.ditto.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.front")
data class FrontProperties(
    val oauthCallbackUrl: String,
)
