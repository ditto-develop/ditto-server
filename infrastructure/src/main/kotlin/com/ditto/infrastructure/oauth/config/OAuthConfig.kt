package com.ditto.infrastructure.oauth.config

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialAuthenticatorFactory
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProvider
import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProviderFactory
import com.ditto.infrastructure.oauth.apple.AppleAuthSender
import com.ditto.infrastructure.oauth.apple.AppleClientSecretGenerator
import com.ditto.infrastructure.oauth.apple.AppleIdTokenVerifier
import com.ditto.infrastructure.oauth.apple.AppleJwksSender
import com.ditto.infrastructure.oauth.apple.AppleNativeAuthenticator
import com.ditto.infrastructure.oauth.apple.AppleNativeFakeAuthenticator
import com.ditto.infrastructure.oauth.apple.AppleOAuthProperties
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationFakeVerifier
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationJwsVerifier
import com.ditto.infrastructure.oauth.apple.AppleServerNotificationVerifier
import com.ditto.infrastructure.oauth.apple.AppleSignedTokenParser
import com.ditto.infrastructure.oauth.apple.AppleTokenClient
import com.ditto.infrastructure.oauth.apple.AppleTokenFakeClient
import com.ditto.infrastructure.oauth.apple.AppleTokenHttpClient
import com.ditto.infrastructure.oauth.apple.AppleWebAuthorizationUrlProvider
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
import java.time.Duration

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
        fun socialAuthorizationUrlProviderFactory(
            oAuthClientFactory: OAuthClientFactory,
            properties: AppleOAuthProperties,
        ): SocialAuthorizationUrlProviderFactory = SocialAuthorizationUrlProviderFactory(
            mapOf(
                SocialProvider.KAKAO to oAuthClientFactory.getClient(SocialProvider.KAKAO),
                SocialProvider.APPLE to AppleWebAuthorizationUrlProvider(properties),
            ),
        )

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

        @Bean
        fun appleServerNotificationVerifier(): AppleServerNotificationVerifier = AppleServerNotificationFakeVerifier()

        @Bean
        fun appleTokenClient(): AppleTokenClient = AppleTokenFakeClient()
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
         * 인가 URL 제공자. 카카오는 [OAuthClient] 가 겸하고, 애플 웹은 인가 URL만 만든다
         * (코드 교환·userinfo 없이 콜백의 ID 토큰을 검증하기 때문).
         */
        @Bean
        fun socialAuthorizationUrlProviderFactory(
            oAuthClientFactory: OAuthClientFactory,
            properties: AppleOAuthProperties,
        ): SocialAuthorizationUrlProviderFactory = SocialAuthorizationUrlProviderFactory(
            mapOf(
                SocialProvider.KAKAO to oAuthClientFactory.getClient(SocialProvider.KAKAO),
                SocialProvider.APPLE to AppleWebAuthorizationUrlProvider(properties),
            ),
        )

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
            signedTokenParser: AppleSignedTokenParser,
        ): AppleIdTokenVerifier = AppleIdTokenVerifier(properties, signedTokenParser)

        @Bean
        fun appleServerNotificationVerifier(
            properties: AppleOAuthProperties,
            signedTokenParser: AppleSignedTokenParser,
        ): AppleServerNotificationVerifier = AppleServerNotificationJwsVerifier(properties, signedTokenParser)

        @Bean
        fun appleSignedTokenParser(
            properties: AppleOAuthProperties,
            jwksSender: AppleJwksSender,
        ): AppleSignedTokenParser = AppleSignedTokenParser(properties, jwksSender)

        @Bean
        fun appleTokenClient(properties: AppleOAuthProperties, authSender: AppleAuthSender): AppleTokenClient =
            AppleTokenHttpClient(properties, AppleClientSecretGenerator(properties), authSender)

        @Bean
        fun appleJwksSender(properties: AppleOAuthProperties): AppleJwksSender =
            httpServiceClient(AppleJwksSender::class.java, properties.connectTimeout, properties.readTimeout)

        @Bean
        fun appleAuthSender(properties: AppleOAuthProperties): AppleAuthSender =
            httpServiceClient(AppleAuthSender::class.java, properties.connectTimeout, properties.readTimeout)

        @Bean
        fun kakaoApiSender(properties: KakaoOAuthProperties): KakaoApiSender =
            httpServiceClient(KakaoApiSender::class.java, properties.connectTimeout, properties.readTimeout)

        private fun <T> httpServiceClient(type: Class<T>, connectTimeout: Duration, readTimeout: Duration): T {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeout)
                setReadTimeout(readTimeout)
            }
            val restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build()

            return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(type)
        }
    }
}
