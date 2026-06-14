package com.ditto.api.admin.config

import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.kakao.KakaoApiSender
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthFakeClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 어드민 로그인 전용 카카오 클라이언트.
 *
 * api 의 유저 로그인은 redirect-uri 가 users/social-login 콜백이라, 어드민은 같은 카카오 앱을 쓰되
 * adminRedirectUri(=/admin/oauth/kakao/callback) 로 redirect-uri 만 바꾼 별도 클라이언트를 사용한다.
 * (유저 로그인은 [com.ditto.infrastructure.oauth.OAuthClientFactory] 를 그대로 사용)
 */
@Configuration
class AdminOAuthClientConfig {

    @Bean
    @Profile("local", "test")
    fun adminKakaoFakeClient(properties: KakaoOAuthProperties): OAuthClient = KakaoOAuthFakeClient(properties)

    @Bean
    @Profile("prod")
    fun adminKakaoRealClient(properties: KakaoOAuthProperties, kakaoApiSender: KakaoApiSender): OAuthClient =
        KakaoOAuthClient(properties.copy(redirectUri = properties.adminRedirectUri), kakaoApiSender)
}
