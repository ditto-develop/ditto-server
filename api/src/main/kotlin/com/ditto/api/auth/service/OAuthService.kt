package com.ditto.api.auth.service

import com.ditto.api.config.FrontProperties
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import java.time.LocalDateTime
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
        accessToken: String,
        signupRequired: Boolean,
    ): String =
        UriComponentsBuilder
            .fromUriString(frontProperties.oauthCallbackUrl)
            .queryParam("accessToken", accessToken)
            .queryParam("signupRequired", signupRequired)
            .build()
            .toUriString()

    /**
     * 제재 회원의 로그인 콜백 URL — 토큰 없이 제재 사실만 전달한다.
     * FE 계약: sanctioned=true + sanctionCode(MEMBER_SUSPENDED|MEMBER_BANNED) + suspendedUntil(정지만, ISO-8601)
     */
    fun getSanctionCallbackUrl(
        sanctionCode: String,
        suspendedUntil: LocalDateTime?,
    ): String {
        val builder = UriComponentsBuilder
            .fromUriString(frontProperties.oauthCallbackUrl)
            .queryParam("sanctioned", true)
            .queryParam("sanctionCode", sanctionCode)
        if (suspendedUntil != null) {
            builder.queryParam("suspendedUntil", suspendedUntil)
        }
        return builder.build().toUriString()
    }
}
