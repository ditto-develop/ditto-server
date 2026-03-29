package com.ditto.api.auth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
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
            "신규 사용자면 회원가입 후 JWT를 발급한다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                result.refreshToken shouldNotBe null
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
                refreshTokenRepository.count() shouldBe 1
            }

            "발급된 JWT의 memberId가 생성된 회원의 id와 일치한다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val memberId = jwtTokenProvider.getMemberId(result.accessToken)
                val member = memberRepository.findAll().first()
                memberId shouldBe member.id
            }

            "기존 사용자면 기존 회원으로 JWT를 발급한다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")
                val memberCountBefore = memberRepository.count()

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                result.refreshToken shouldNotBe null
                memberRepository.count() shouldBe memberCountBefore
            }

            "기존 사용자 재로그인 시 socialAccount가 추가 생성되지 않는다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")
                val socialAccountCountBefore = socialAccountRepository.count()

                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                socialAccountRepository.count() shouldBe socialAccountCountBefore
            }
        }
    },
)
