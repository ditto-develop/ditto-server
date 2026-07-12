package com.ditto.api.auth.facade

import com.ditto.api.auth.dto.OAuthLoginResult
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.springframework.stereotype.Component

@Component
class OAuthFacade(
    private val oAuthService: OAuthService,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
    private val serverTimeProvider: ServerTimeProvider,
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

        // 제재 회원은 토큰을 발급하지 않고 콜백으로 제재 사실만 전달한다. (해제일 경과한 정지는 통과)
        if (member.isBanned()) {
            return sanctionLoginResult(ErrorCode.MEMBER_BANNED, member)
        }
        if (member.isSuspendedAt(serverTimeProvider.now())) {
            return sanctionLoginResult(ErrorCode.MEMBER_SUSPENDED, member)
        }

        val accessToken = jwtTokenProvider.generateAccessToken(member.id, member.role)
        val refreshToken = authService.createRefreshToken(member.id)
        val redirectUrl = oAuthService.getAuthCallbackUrl(accessToken, signupRequired = member.isPending())

        return OAuthLoginResult(redirectUrl = redirectUrl, refreshToken = refreshToken.token)
    }

    private fun sanctionLoginResult(sanctionCode: ErrorCode, member: Member): OAuthLoginResult {
        val redirectUrl = oAuthService.getSanctionCallbackUrl(
            sanctionCode = sanctionCode.name,
            suspendedUntil = member.suspendedUntil,
        )
        return OAuthLoginResult(redirectUrl = redirectUrl, refreshToken = null)
    }
}
