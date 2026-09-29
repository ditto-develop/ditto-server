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
import java.time.Duration
import java.time.LocalDateTime

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
        // 서버 잘못이 아니다 — 로그아웃·쿠키 삭제·이미 회전된 토큰이면 정상적으로 도달한다.
        // ErrorException 이면 스택트레이스가 ERROR 로 남아 진짜 장애처럼 보인다.
        val existedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
            ?: throw WarnException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)

        val now = LocalDateTime.now()
        if (existedRefreshToken.isExpired(now)) {
            throw WarnException(ErrorCode.REFRESH_TOKEN_EXPIRED)
        }

        // 정리를 만료 단축보다 먼저 한다. 반대면 행 하나를 잡은 채 회원 범위를 잠그게 돼,
        // 같은 회원의 다른 토큰 refresh·로그아웃과 동시에 돌 때 서로 교착할 수 있다.
        refreshTokenRepository.deleteExpiredByMemberId(existedRefreshToken.memberId, now)

        // 바로 지우지 않고 잠깐 더 받아준다. 새 쿠키를 담은 응답이 유실되거나(갱신 중 페이지 이동)
        // 두 요청이 같은 쿠키로 동시에 오면, 즉시 삭제로는 그 기기의 세션이 끝난다.
        existedRefreshToken.shortenExpiry(now.plus(ROTATION_GRACE))

        val member = memberRepository.findById(existedRefreshToken.memberId)
            .orElseThrow { ErrorException(ErrorCode.UNAUTHORIZED_ERROR) }

        // 제재 회원의 재발급 우회 봉쇄 — refresh 경로는 JWT 필터를 지나지 않는다.
        // 예외 시 트랜잭션 롤백으로 위 만료 단축도 되돌아가 토큰은 남지만, 남아 있어도 이 게이트가 사용을 계속 거부한다.
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

    companion object {
        private val ROTATION_GRACE: Duration = Duration.ofSeconds(30)
    }
}
