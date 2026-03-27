package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.springframework.stereotype.Component

@Component
class OAuthFacade(
    private val oAuthService: OAuthService,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthService.getAuthorizationUrl(provider)

    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val memberId = memberSocialAccountService.findOrCreateMember(provider, userInfo.id, userInfo.nickname)
        val token = jwtTokenProvider.generateToken(memberId)
        return OAuthLoginResponse(accessToken = token)
    }
}
