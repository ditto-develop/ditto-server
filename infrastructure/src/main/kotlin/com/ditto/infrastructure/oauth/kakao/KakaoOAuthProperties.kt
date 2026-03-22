package com.ditto.infrastructure.oauth.kakao

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.oauth.kakao")
data class KakaoOAuthProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
)
