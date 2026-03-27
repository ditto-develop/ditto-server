package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import org.springframework.stereotype.Service

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthClientFactory.getClient(provider).getAuthorizationUrl()

    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val client = oAuthClientFactory.getClient(provider)

        val accessToken = client.getAccessToken(code)
        val userInfo = client.getUserInfo(accessToken)

        val memberId = memberSocialAccountService.findOrCreateMember(provider, userInfo.id, userInfo.nickname)
        val token = jwtTokenProvider.generateToken(memberId)
        return OAuthLoginResponse(accessToken = token)
    }
}
