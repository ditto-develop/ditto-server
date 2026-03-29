package com.ditto.api.auth

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import org.springframework.stereotype.Service

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthClientFactory.getClient(provider).getAuthorizationUrl()

    fun getOAuthUserInfo(provider: SocialProvider, code: String): OAuthUserInfo {
        val client = oAuthClientFactory.getClient(provider)
        val accessToken = client.getAccessToken(code)
        return client.getUserInfo(accessToken)
    }
}
