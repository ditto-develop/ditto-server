package com.ditto.api.auth

import com.ditto.api.auth.dto.TokenRefreshRequest
import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class AuthServiceTest(
    private val authService: AuthService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val socialAccountRepository: SocialAccountRepository,
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "토큰 갱신" - {
            "유효한 리프레시 토큰으로 새 토큰 쌍을 발급한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
                val refreshToken = authService.createRefreshToken(member.id)

                val result = authService.refresh(TokenRefreshRequest(refreshToken = refreshToken.token))

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                jwtTokenProvider.getProviderUserId(result.accessToken) shouldBe "providerUserId"
                result.refreshToken shouldNotBe refreshToken.token
            }

            "존재하지 않는 리프레시 토큰이면 예외가 발생한다" {
                val exception = shouldThrow<ErrorException> {
                    authService.refresh(TokenRefreshRequest(refreshToken = "non-existent-token"))
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }

            "만료된 리프레시 토큰이면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
                val expiredToken = RefreshToken.create(
                    memberId = member.id,
                    token = "expired-token",
                    expiresAt = LocalDateTime.now().minusDays(1),
                )
                refreshTokenRepository.save(expiredToken)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(TokenRefreshRequest(refreshToken = "expired-token"))
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_EXPIRED
            }

            "갱신 후 이전 리프레시 토큰은 사용할 수 없다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
                val refreshToken = authService.createRefreshToken(member.id)
                val oldToken = refreshToken.token

                authService.refresh(TokenRefreshRequest(refreshToken = oldToken))

                val exception = shouldThrow<ErrorException> {
                    authService.refresh(TokenRefreshRequest(refreshToken = oldToken))
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }
        }

        "로그아웃" - {
            "로그아웃하면 해당 회원의 모든 토큰이 삭제된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
                val refreshToken = authService.createRefreshToken(member.id)

                authService.logout(SocialProvider.KAKAO, "providerUserId")

                refreshTokenRepository.findByToken(refreshToken.token) shouldBe null
            }

            "로그아웃 후 같은 리프레시 토큰으로 갱신할 수 없다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "providerUserId"))
                val refreshToken = authService.createRefreshToken(member.id)

                authService.logout(SocialProvider.KAKAO, "providerUserId")

                val exception = shouldThrow<ErrorException> {
                    authService.refresh(TokenRefreshRequest(refreshToken = refreshToken.token))
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }
        }
    },
)
