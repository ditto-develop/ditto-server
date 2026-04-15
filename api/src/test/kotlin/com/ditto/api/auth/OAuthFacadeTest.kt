package com.ditto.api.auth

import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.OAuthClientFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class OAuthFacadeTest(
    private val oAuthFacade: OAuthFacade,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "인가 URL 조회" - {
            "카카오 인가 URL을 반환한다" {
                val result = oAuthFacade.getAuthorizationUrl(SocialProvider.KAKAO)

                result shouldNotBe null
                result.contains("client_id=test-client-id") shouldBe true
            }

            "지원하지 않는 제공자면 예외가 발생한다" {
                val facadeWithNoClients = OAuthFacade(
                    oAuthService = OAuthService(OAuthClientFactory(emptyMap())),
                    memberSocialAccountService = memberSocialAccountService,
                    jwtTokenProvider = jwtTokenProvider,
                    authService = authService,
                )

                val exception = shouldThrow<ErrorException> {
                    facadeWithNoClients.getAuthorizationUrl(SocialProvider.KAKAO)
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }

        "소셜 로그인" - {
            "신규 사용자면 PENDING 상태로 생성되고 토큰을 발급하지 않는다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldBe null
                result.refreshToken shouldBe null
                memberRepository.count() shouldBe 1
                memberRepository.findAll().first().status shouldBe MemberStatus.PENDING
                socialAccountRepository.count() shouldBe 1
                refreshTokenRepository.count() shouldBe 0
            }

            "PENDING 사용자가 재로그인하면 토큰을 발급하지 않는다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldBe null
                result.refreshToken shouldBe null
                memberRepository.count() shouldBe 1
            }

            "ACTIVE 사용자면 JWT를 발급한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com")
                member.activate()
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken!!) shouldBe true
                result.refreshToken shouldNotBe null
                refreshTokenRepository.count() shouldBe 1
            }

            "ACTIVE 사용자의 JWT에 memberId가 포함된다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com")
                member.activate()
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                jwtTokenProvider.getMemberId(result.accessToken!!) shouldBe member.id
            }
        }
    },
)
