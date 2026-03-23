package com.ditto.infrastructure.oauth.config

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
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
    }

    @Profile("prod")
    @Configuration
    inner class oAuthConfig {

        @Bean
        fun oAuthClientFactory(properties: KakaoOAuthProperties, client: KakaoApiSender): OAuthClientFactory {
            return OAuthClientFactory(
                mapOf(
                    SocialProvider.KAKAO to KakaoOAuthClient(properties, client),
                ),
            )
        }

        @Bean
        fun kakaoApiSender(): KakaoApiSender {
            val requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(5))
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
