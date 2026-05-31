package com.ditto.api.auth.service

import com.ditto.api.config.FrontProperties
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
    private val frontProperties: FrontProperties,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthClientFactory.getClient(provider).getAuthorizationUrl()

    fun getOAuthUserInfo(
        provider: SocialProvider,
        code: String,
    ): OAuthUserInfo {
        val client = oAuthClientFactory.getClient(provider)
        val accessToken = client.getAccessToken(code)
        return client.getUserInfo(accessToken)
    }

    fun getAuthCallbackUrl(
        accessToken: String?,
        refreshToken: String?,
    ): String =
        UriComponentsBuilder
            .fromUriString(frontProperties.oauthCallbackUrl)
            .apply {
                accessToken?.let { queryParam("accessToken", it) }
                refreshToken?.let { queryParam("refreshToken", it) }
            }.build()
            .toUriString()
}
