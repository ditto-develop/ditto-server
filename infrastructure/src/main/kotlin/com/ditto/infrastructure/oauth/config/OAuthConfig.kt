package com.ditto.infrastructure.oauth.config

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialAuthenticatorFactory
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.apple.AppleIdTokenVerifier
import com.ditto.infrastructure.oauth.apple.AppleJwksSender
import com.ditto.infrastructure.oauth.apple.AppleNativeAuthenticator
import com.ditto.infrastructure.oauth.apple.AppleNativeFakeAuthenticator
import com.ditto.infrastructure.oauth.apple.AppleOAuthProperties
import com.ditto.infrastructure.oauth.kakao.KakaoNativeAuthenticator
import com.ditto.infrastructure.oauth.kakao.KakaoApiSender
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthFakeClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
@EnableConfigurationProperties(
    KakaoOAuthProperties::class,
    AppleOAuthProperties::class,
)
class OAuthConfig {

    @Profile("local", "test")
    @Configuration
    inner class FakeOAuthConfig {

        @Bean
        fun oAuthClientFactory(properties: KakaoOAuthProperties): OAuthClientFactory {
            return OAuthClientFactory(
                mapOf(
                    SocialProvider.KAKAO to KakaoOAuthFakeClient(properties),
                ),
            )
        }

        @Bean
        fun nativeSocialAuthenticatorFactory(
            oAuthClientFactory: OAuthClientFactory,
        ): NativeSocialAuthenticatorFactory = NativeSocialAuthenticatorFactory(
            mapOf(
                SocialProvider.KAKAO to KakaoNativeAuthenticator(
                    oAuthClientFactory.getClient(SocialProvider.KAKAO),
                ),
                SocialProvider.APPLE to AppleNativeFakeAuthenticator(),
            ),
        )
    }

    @Profile("prod")
    @Configuration
    inner class OAuthConfig {

        @Bean
        fun oAuthClientFactory(properties: KakaoOAuthProperties, client: KakaoApiSender): OAuthClientFactory {
            return OAuthClientFactory(
                mapOf(
                    SocialProvider.KAKAO to KakaoOAuthClient(properties, client),
                ),
            )
        }

        /**
         * 네이티브 로그인 인증기. 카카오는 액세스 토큰으로 me API 를, 애플은 ID 토큰 서명을 검증한다 —
         * 두 흐름이 달라 [OAuthClient] 가 아니라 [NativeSocialAuthenticator] 로 묶는다.
         */
        @Bean
        fun nativeSocialAuthenticatorFactory(
            oAuthClientFactory: OAuthClientFactory,
            appleIdTokenVerifier: AppleIdTokenVerifier,
        ): NativeSocialAuthenticatorFactory = NativeSocialAuthenticatorFactory(
            mapOf(
                SocialProvider.KAKAO to KakaoNativeAuthenticator(
                    oAuthClientFactory.getClient(SocialProvider.KAKAO),
                ),
                SocialProvider.APPLE to AppleNativeAuthenticator(appleIdTokenVerifier),
            ),
        )

        @Bean
        fun appleIdTokenVerifier(
            properties: AppleOAuthProperties,
            jwksSender: AppleJwksSender,
        ): AppleIdTokenVerifier = AppleIdTokenVerifier(properties, jwksSender)

        @Bean
        fun appleJwksSender(properties: AppleOAuthProperties): AppleJwksSender {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.readTimeout)
            }
            val restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build()

            return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(AppleJwksSender::class.java)
        }

        @Bean
        fun kakaoApiSender(properties: KakaoOAuthProperties): KakaoApiSender {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeout)
                setReadTimeout(properties.readTimeout)
            }
            val restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build()

            return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(KakaoApiSender::class.java)
        }

    }
}
