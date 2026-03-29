package com.ditto.api.auth

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
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthService.getAuthorizationUrl(provider)

    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val memberId = memberSocialAccountService.findOrCreateMember(provider, userInfo.id, userInfo.nickname)
        val accessToken = jwtTokenProvider.generateToken(memberId)
        val refreshToken = authService.createRefreshToken(memberId)
        return OAuthLoginResponse(accessToken = accessToken, refreshToken = refreshToken.token)
    }
}
