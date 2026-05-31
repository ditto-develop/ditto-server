package com.ditto.api.auth.facade

import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.springframework.stereotype.Component

@Component
class OAuthFacade(
    private val oAuthService: OAuthService,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String = oAuthService.getAuthorizationUrl(provider)

    fun login(
        provider: SocialProvider,
        code: String,
    ): String {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val member = memberSocialAccountService.findOrCreateMember(provider, userInfo.id, userInfo.email)

        if (member.isPending()) {
            return oAuthService.getAuthCallbackUrl(accessToken = null, refreshToken = null)
        }

        val accessToken = jwtTokenProvider.generateAccessToken(member.id)
        val refreshToken = authService.createRefreshToken(member.id)
        return oAuthService.getAuthCallbackUrl(accessToken, refreshToken.token)
    }
}
