package com.ditto.api.auth.facade

import com.ditto.api.auth.dto.OAuthLoginResponse
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
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthService.getAuthorizationUrl(provider)

    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val memberId = memberSocialAccountService.findOrCreateMember(provider, userInfo.id, userInfo.nickname)
        val accessToken = jwtTokenProvider.generateAccessToken(
            providerUserId = userInfo.id,
            provider = provider,
        )
        val refreshToken = authService.createRefreshToken(memberId)
        return OAuthLoginResponse(accessToken = accessToken, refreshToken = refreshToken.token)
    }
}
