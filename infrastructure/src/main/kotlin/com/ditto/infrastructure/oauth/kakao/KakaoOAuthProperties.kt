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
    /**
     * 인가 요청 scope. 앱에 안 켜진 동의항목을 넘기면 카카오가 로그인을 거부한다.
     *
     * 이름·성별·연령대·생일·전화번호는 비즈 앱 전용이라 기본값은 profile_nickname 하나다.
     * 이메일(account_email)은 일반 앱도 콘솔에서 켤 수 있고, prod는 KAKAO_SCOPES 환경변수로 넣는다.
     * 비우면 scope 없이 요청해서 앱 설정을 그대로 따른다.
     */
    val scopes: List<String> = listOf("profile_nickname"),
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
)
