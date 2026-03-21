package com.ditto.infrastructure.oauth

import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.kakao.KakaoApiClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthFakeClient
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(
    KakaoOAuthProperties::class,
)
class OAuthConfig {

    @Profile("local", "test")
    @Configuration
    inner class FakeOAuthConfig {

        @Bean
        fun oAuthClientFactory(): OAuthClientFactory {
            return OAuthClientFactory(
                mapOf(
                    SocialProvider.KAKAO to KakaoOAuthFakeClient(),
                ),
            )
        }
    }

    @Profile("prod")
    @Configuration
    inner class OAuthConfig {

        @Bean
        fun oAuthClientFactory(properties: KakaoOAuthProperties, client: KakaoApiClient): OAuthClientFactory {
            return OAuthClientFactory(
                mapOf(
                    SocialProvider.KAKAO to KakaoOAuthClient(properties, client),
                ),
            )
        }
    }

}
