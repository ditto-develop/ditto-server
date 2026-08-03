package com.ditto.api.auth.service

import com.ditto.api.auth.dto.TokenRefreshResult
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
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
    fun refresh(refreshToken: String): TokenRefreshResult {
        val existedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
            ?: throw ErrorException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)

        if (existedRefreshToken.isExpired()) {
            throw WarnException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        refreshTokenRepository.delete(existedRefreshToken)

        val member = memberRepository.findById(existedRefreshToken.memberId)
            .orElseThrow { ErrorException(ErrorCode.UNAUTHORIZED_ERROR) }

        // 제재 회원의 재발급 우회 봉쇄 — refresh 경로는 JWT 필터를 지나지 않는다.
        // 예외 시 트랜잭션 롤백으로 위 회전 삭제도 되돌아가 토큰은 남지만, 남아 있어도 이 게이트가 사용을 계속 거부한다.
        // (세션 전량 회수는 제재 적용 트랜잭션의 몫)
        if (member.isLeft()) {
            throw WarnException(ErrorCode.MEMBER_LEFT)
        }
        if (member.isBanned()) {
            throw WarnException(ErrorCode.MEMBER_BANNED)
        }
        if (member.isSuspendedAt(serverTimeProvider.now())) {
            throw WarnException(ErrorCode.MEMBER_SUSPENDED)
        }

        val newAccessToken = jwtTokenProvider.generateAccessToken(member.id, member.role)
        val newRefreshToken = createRefreshToken(member.id)

        return TokenRefreshResult(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken.token,
        )
    }
}
