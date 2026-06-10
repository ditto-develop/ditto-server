package com.ditto.api.auth.facade

import com.ditto.api.auth.dto.OAuthLoginResult
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
    ): OAuthLoginResult {
        val userInfo = oAuthService.getOAuthUserInfo(provider, code)
        val member = memberSocialAccountService.findOrCreateMember(
            provider = provider,
            providerUserId = userInfo.id,
            email = userInfo.email,
            birthDate = userInfo.birthDate?.atStartOfDay(),
            name = userInfo.name,
            phoneNumber = userInfo.phoneNumber,
            gender = userInfo.gender,
        )

        val accessToken = jwtTokenProvider.generateAccessToken(member.id, member.role)
        val refreshToken = authService.createRefreshToken(member.id)
        val redirectUrl = oAuthService.getAuthCallbackUrl(accessToken, signupRequired = member.isPending())

        return OAuthLoginResult(redirectUrl = redirectUrl, refreshToken = refreshToken.token)
    }
}
