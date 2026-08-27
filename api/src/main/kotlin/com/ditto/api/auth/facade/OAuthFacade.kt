package com.ditto.api.auth.facade

import com.ditto.api.auth.dto.NativeSocialLoginResponse
import com.ditto.api.auth.dto.NativeSocialLoginResult
import com.ditto.api.auth.dto.OAuthLoginResult
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthUserInfo
import org.springframework.stereotype.Component

@Component
class OAuthFacade(
    private val oAuthService: OAuthService,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
    private val serverTimeProvider: ServerTimeProvider,
    private val sanctionExpiryService: SanctionExpiryService,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String = oAuthService.getAuthorizationUrl(provider)

    /**
     * 웹 리다이렉트 로그인 — 인가 코드를 토큰으로 교환한 뒤 프론트 콜백 URL로 보낸다.
     */
    fun login(
        provider: SocialProvider,
        code: String,
    ): OAuthLoginResult {
        val member = findOrCreateMember(provider, oAuthService.getOAuthUserInfo(provider, code))

        // 제재 회원은 토큰을 발급하지 않고 콜백으로 제재 사실만 전달한다. (해제일 경과한 정지는 통과)
        blockingSanctionOf(member)?.let { return sanctionLoginResult(it, member) }

        val tokens = issueTokens(member)
        val redirectUrl = oAuthService.getAuthCallbackUrl(tokens.accessToken, signupRequired = member.isPending())

        return OAuthLoginResult(redirectUrl = redirectUrl, refreshToken = tokens.refreshToken)
    }

    /**
     * 네이티브 앱 로그인 — 소셜 SDK가 이미 받아온 액세스 토큰을 우리 토큰으로 교환한다.
     *
     * 리다이렉트가 없다는 점만 [login]과 다르고, 회원 생성·제재 판정·토큰 발급은 같은 경로를 탄다.
     * 그래서 `signupRequired`·`sanctioned` 의미도 콜백 계약과 동일하다.
     */
    fun loginWithNativeToken(
        provider: SocialProvider,
        socialAccessToken: String,
    ): NativeSocialLoginResult {
        val userInfo = oAuthService.getOAuthUserInfoByAccessToken(provider, socialAccessToken)
        val member = findOrCreateMember(provider, userInfo)

        blockingSanctionOf(member)?.let { sanctionCode ->
            return NativeSocialLoginResult(
                response = NativeSocialLoginResponse(
                    accessToken = null,
                    signupRequired = false,
                    sanctioned = true,
                    sanctionCode = sanctionCode.name,
                    // 정지만 해제 예정일이 있다. 영구 차단은 null.
                    suspendedUntil = member.suspendedUntil,
                ),
                refreshToken = null,
            )
        }

        val tokens = issueTokens(member)
        return NativeSocialLoginResult(
            response = NativeSocialLoginResponse(
                accessToken = tokens.accessToken,
                signupRequired = member.isPending(),
                sanctioned = false,
            ),
            refreshToken = tokens.refreshToken,
        )
    }

    private fun findOrCreateMember(
        provider: SocialProvider,
        userInfo: OAuthUserInfo,
    ): Member = memberSocialAccountService.findOrCreateMember(
        provider = provider,
        providerUserId = userInfo.id,
        email = userInfo.email,
        birthDate = userInfo.birthDate?.atStartOfDay(),
        name = userInfo.name,
        phoneNumber = userInfo.phoneNumber,
        gender = userInfo.gender,
    )

    /**
     * 로그인을 막는 제재 코드를 반환한다. 막을 제재가 없으면 null이며,
     * 그 과정에서 해제일이 지난 정지는 원복한다 (lazy 만료의 개별 원복 훅 — ADR 0009).
     */
    private fun blockingSanctionOf(member: Member): ErrorCode? {
        val now = serverTimeProvider.now()
        if (member.isBanned()) return ErrorCode.MEMBER_BANNED
        if (member.isSuspendedAt(now)) return ErrorCode.MEMBER_SUSPENDED

        sanctionExpiryService.reinstateIfExpired(member, now)
        return null
    }

    private fun issueTokens(member: Member): IssuedTokens = IssuedTokens(
        accessToken = jwtTokenProvider.generateAccessToken(member.id, member.role),
        refreshToken = authService.createRefreshToken(member.id).token,
    )

    private fun sanctionLoginResult(sanctionCode: ErrorCode, member: Member): OAuthLoginResult {
        val redirectUrl = oAuthService.getSanctionCallbackUrl(
            sanctionCode = sanctionCode.name,
            suspendedUntil = member.suspendedUntil,
        )
        return OAuthLoginResult(redirectUrl = redirectUrl, refreshToken = null)
    }

    private data class IssuedTokens(
        val accessToken: String,
        val refreshToken: String,
    )
}
