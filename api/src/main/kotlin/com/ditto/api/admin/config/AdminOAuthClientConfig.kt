package com.ditto.api.admin.config

import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProvider
import com.ditto.infrastructure.oauth.apple.AppleOAuthProperties
import com.ditto.infrastructure.oauth.apple.AppleWebAuthorizationUrlProvider
import com.ditto.infrastructure.oauth.kakao.KakaoApiSender
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthFakeClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 어드민 로그인 전용 소셜 클라이언트.
 *
 * api 의 유저 로그인은 redirect-uri 가 users/social-login 콜백이라, 어드민은 같은 앱(카카오 앱 / 애플 Services ID)을
 * 쓰되 **돌아올 주소만 어드민 콜백으로 바꾼** 별도 빈을 쓴다.
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

    /**
     * 애플은 카카오처럼 프로파일을 나누지 않는다 — 인가 URL 생성은 외부 호출이 없는 순수 문자열 조립이고,
     * 콜백으로 온 ID 토큰 검증만 프로파일마다 다른 [com.ditto.infrastructure.oauth.NativeSocialAuthenticator] 가 맡는다.
     */
    @Bean
    fun adminAppleAuthorizationUrlProvider(properties: AppleOAuthProperties): SocialAuthorizationUrlProvider =
        AppleWebAuthorizationUrlProvider(properties.copy(webRedirectUri = properties.adminWebRedirectUri))
}
