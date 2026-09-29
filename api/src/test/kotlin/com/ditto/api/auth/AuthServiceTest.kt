package com.ditto.api.auth

import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.RefreshTokenFixture
import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.date.shouldBeBefore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class AuthServiceTest(
    private val authService: AuthService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "토큰 갱신" - {
            "유효한 리프레시 토큰으로 새 토큰 쌍을 발급한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val refreshToken = authService.createRefreshToken(member.id)

                val result = authService.refresh(refreshToken.token)

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                jwtTokenProvider.getMemberId(result.accessToken) shouldBe member.id
                result.refreshToken shouldNotBe refreshToken.token
            }

            "존재하지 않는 리프레시 토큰이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    authService.refresh("non-existent-token")
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }

            "만료된 리프레시 토큰이면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val expiredToken = RefreshToken.create(
                    memberId = member.id,
                    token = "expired-token",
                    expiresAt = LocalDateTime.now().minusDays(1),
                )
                refreshTokenRepository.save(expiredToken)

                val exception = shouldThrow<WarnException> {
                    authService.refresh("expired-token")
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_EXPIRED
            }

            "갱신 직후에는 이전 리프레시 토큰으로 한 번 더 갱신할 수 있다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val oldToken = authService.createRefreshToken(member.id).token

                val first = authService.refresh(oldToken)
                val second = authService.refresh(oldToken)

                jwtTokenProvider.getMemberId(second.accessToken) shouldBe member.id
                second.refreshToken shouldNotBe first.refreshToken
                refreshTokenRepository.findByToken(first.refreshToken) shouldNotBe null
            }

            "갱신하면 이전 리프레시 토큰은 30초 뒤에 만료된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val oldToken = authService.createRefreshToken(member.id).token
                val before = LocalDateTime.now()

                authService.refresh(oldToken)

                val expiresAt = refreshTokenRepository.findByToken(oldToken)?.expiresAt
                    ?: error("유예 중인 토큰이 남아 있어야 한다")
                expiresAt shouldBeAfter before.plusSeconds(29)
                expiresAt shouldBeBefore LocalDateTime.now().plusSeconds(31)
            }

            "유예 시간이 지난 이전 리프레시 토큰은 사용할 수 없다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val oldToken = authService.createRefreshToken(member.id).token
                authService.refresh(oldToken)
                val rotated = refreshTokenRepository.findByToken(oldToken)
                    ?: error("유예 중인 토큰이 남아 있어야 한다")
                rotated.shortenExpiry(LocalDateTime.now().minusSeconds(1))
                refreshTokenRepository.save(rotated)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(oldToken)
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_EXPIRED
            }

            "갱신하면 그 회원의 만료된 리프레시 토큰을 지운다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
                val otherMember = memberRepository.save(Member(nickname = "다른유저", email = "other@kakao.com"))
                val expired = refreshTokenRepository.save(
                    RefreshTokenFixture.create(memberId = member.id, expiresAt = LocalDateTime.now().minusDays(1)),
                )
                val othersExpired = refreshTokenRepository.save(
                    RefreshTokenFixture.create(memberId = otherMember.id, expiresAt = LocalDateTime.now().minusDays(1)),
                )
                val validToken = authService.createRefreshToken(member.id).token

                authService.refresh(validToken)

                refreshTokenRepository.findByToken(expired.token) shouldBe null
                refreshTokenRepository.findByToken(othersExpired.token) shouldNotBe null
                refreshTokenRepository.findByToken(validToken) shouldNotBe null
            }

            "이용 정지 중인 회원은 토큰을 갱신할 수 없다 — 재시도해도 계속 거부된다" {
                val member = memberRepository.save(
                    Member(nickname = "정지유저").apply {
                        activate()
                        suspendUntil(LocalDateTime.now().plusDays(7))
                    },
                )
                val refreshToken = authService.createRefreshToken(member.id)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(refreshToken.token)
                }
                exception.errorCode shouldBe ErrorCode.MEMBER_SUSPENDED

                // 롤백으로 토큰은 남지만 게이트가 사용을 계속 거부한다.
                val retryException = shouldThrow<WarnException> {
                    authService.refresh(refreshToken.token)
                }
                retryException.errorCode shouldBe ErrorCode.MEMBER_SUSPENDED
            }

            "영구 차단 회원은 토큰을 갱신할 수 없다" {
                val member = memberRepository.save(Member(nickname = "차단유저").apply { activate(); ban() })
                val refreshToken = authService.createRefreshToken(member.id)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(refreshToken.token)
                }

                exception.errorCode shouldBe ErrorCode.MEMBER_BANNED
            }

            "정지 해제 예정일이 지난 회원은 토큰을 갱신할 수 있다" {
                val member = memberRepository.save(
                    Member(nickname = "만료정지유저").apply {
                        activate()
                        suspendUntil(LocalDateTime.now().minusDays(1))
                    },
                )
                val refreshToken = authService.createRefreshToken(member.id)

                val result = authService.refresh(refreshToken.token)

                jwtTokenProvider.isValid(result.accessToken) shouldBe true
            }
        }

        "로그아웃" - {
            "로그아웃하면 해당 회원의 모든 토큰이 삭제된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                val refreshToken = authService.createRefreshToken(member.id)

                authService.logout(member.id)

                refreshTokenRepository.findByToken(refreshToken.token) shouldBe null
            }

            "로그아웃하면 해당 회원의 여러 토큰이 모두 삭제된다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                val token1 = authService.createRefreshToken(member.id)
                val token2 = authService.createRefreshToken(member.id)
                val token3 = authService.createRefreshToken(member.id)

                authService.logout(member.id)

                refreshTokenRepository.findByToken(token1.token) shouldBe null
                refreshTokenRepository.findByToken(token2.token) shouldBe null
                refreshTokenRepository.findByToken(token3.token) shouldBe null
            }

            "갱신 직후 로그아웃하면 유예 중인 이전 토큰도 사용할 수 없다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                val oldToken = authService.createRefreshToken(member.id).token
                authService.refresh(oldToken)

                authService.logout(member.id)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(oldToken)
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }

            "로그아웃 후 같은 리프레시 토큰으로 갱신할 수 없다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                val refreshToken = authService.createRefreshToken(member.id)

                authService.logout(member.id)

                val exception = shouldThrow<WarnException> {
                    authService.refresh(refreshToken.token)
                }
                exception.errorCode shouldBe ErrorCode.REFRESH_TOKEN_NOT_FOUND
            }
        }
    },
)
