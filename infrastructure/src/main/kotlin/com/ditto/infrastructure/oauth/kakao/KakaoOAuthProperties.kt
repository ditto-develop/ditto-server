package com.ditto.infrastructure.oauth.kakao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ditto.oauth.kakao")
data class KakaoOAuthProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    // 어드민(세션 로그인)용 별도 redirect-uri. 같은 카카오 앱을 쓰되 콜백 경로만 다르다(/admin/oauth/kakao/callback).
    val adminRedirectUri: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
)
