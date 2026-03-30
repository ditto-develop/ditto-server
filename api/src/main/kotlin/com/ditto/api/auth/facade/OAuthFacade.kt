package com.ditto.api.auth.facade

import com.ditto.api.auth.dto.OAuthLoginResponse
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Component

@Component
class OAuthFacade(
    private val oAuthService: OAuthService,
    private val socialAccountRepository: SocialAccountRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthService.getAuthorizationUrl(provider)

    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val existingAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, userInfo.id)
            ?: return OAuthLoginResponse()

        val accessToken = jwtTokenProvider.generateAccessToken(
            providerUserId = userInfo.id,
            provider = provider,
        )
        val refreshToken = authService.createRefreshToken(existingAccount.memberId)
        return OAuthLoginResponse(accessToken = accessToken, refreshToken = refreshToken.token)
    }
}
