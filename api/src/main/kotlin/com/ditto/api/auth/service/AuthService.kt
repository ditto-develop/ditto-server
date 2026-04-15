package com.ditto.api.auth.service

import com.ditto.api.auth.dto.TokenRefreshRequest
import com.ditto.api.auth.dto.TokenRefreshResponse
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
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
    fun logout(memberId: Long) {
        refreshTokenRepository.deleteAllByMemberId(memberId)
    }

    @Transactional
    fun refresh(request: TokenRefreshRequest): TokenRefreshResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw ErrorException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        if (refreshToken.isExpired()) {
            throw WarnException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        refreshTokenRepository.delete(refreshToken)

        val newAccessToken = jwtTokenProvider.generateAccessToken(refreshToken.memberId)
        val newRefreshToken = createRefreshToken(refreshToken.memberId)

        return TokenRefreshResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken.token,
        )
    }
}
