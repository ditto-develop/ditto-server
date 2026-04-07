package com.ditto.api.auth.service

import com.ditto.api.auth.dto.TokenRefreshRequest
import com.ditto.api.auth.dto.TokenRefreshResponse
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val socialAccountRepository: SocialAccountRepository,
) {

    @Transactional
    fun createRefreshToken(memberId: Long): RefreshToken {
        val token = jwtTokenProvider.generateRefreshToken()
        val expiresAt = jwtTokenProvider.createRefreshTokenExpiresAt()
        val refreshToken = RefreshToken.create(
            memberId = memberId,
            token = token,
            expiresAt = expiresAt,
        )
        return refreshTokenRepository.save(refreshToken)
    }

    @Transactional
    fun logout(provider: SocialProvider, providerUserId: String) {
        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
            ?: throw ErrorException(ErrorCode.UNAUTHORIZED_ERROR)
        refreshTokenRepository.deleteAllByMemberId(socialAccount.memberId)
    }

    @Transactional
    fun refresh(request: TokenRefreshRequest): TokenRefreshResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw ErrorException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        if (refreshToken.isExpired()) {
            throw WarnException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        refreshTokenRepository.delete(refreshToken)

        val socialAccount = socialAccountRepository.findByMemberId(refreshToken.memberId)
            ?: throw ErrorException(ErrorCode.INTERNAL_ERROR)

        val newAccessToken = jwtTokenProvider.generateAccessToken(
            providerUserId = socialAccount.providerUserId,
            provider = socialAccount.provider,
        )
        val newRefreshToken = createRefreshToken(refreshToken.memberId)

        return TokenRefreshResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken.token,
        )
    }
}
