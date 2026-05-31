package com.ditto.api.auth

import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.FrontProperties
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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.web.util.UriComponentsBuilder
import javax.sql.DataSource

class OAuthFacadeTest(
    private val oAuthFacade: OAuthFacade,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
    private val frontProperties: FrontProperties,
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
                    oAuthService = OAuthService(OAuthClientFactory(emptyMap()), frontProperties),
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
            "신규 사용자면 PENDING 상태로 생성되고 토큰 없는 redirect URL을 반환한다" {
                val redirectUrl = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                redirectUrl shouldNotContain "accessToken"
                redirectUrl shouldNotContain "refreshToken"
                memberRepository.count() shouldBe 1
                memberRepository.findAll().first().status shouldBe MemberStatus.PENDING
                socialAccountRepository.count() shouldBe 1
                refreshTokenRepository.count() shouldBe 0
            }

            "PENDING 사용자가 재로그인하면 토큰 없는 redirect URL을 반환한다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val redirectUrl = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                redirectUrl shouldNotContain "accessToken"
                redirectUrl shouldNotContain "refreshToken"
                memberRepository.count() shouldBe 1
            }

            "ACTIVE 사용자면 토큰을 포함한 redirect URL을 반환한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com")
                member.activate()
                memberRepository.save(member)

                val redirectUrl = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(redirectUrl).build().queryParams
                params["accessToken"]?.first() shouldNotBe null
                params["refreshToken"]?.first() shouldNotBe null
                jwtTokenProvider.isValid(params["accessToken"]!!.first()) shouldBe true
                refreshTokenRepository.count() shouldBe 1
            }

            "ACTIVE 사용자의 redirect URL이 설정된 프론트 콜백 URL로 시작한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com")
                member.activate()
                memberRepository.save(member)

                val redirectUrl = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                redirectUrl shouldContain frontProperties.oauthCallbackUrl
            }

            "ACTIVE 사용자의 JWT에 memberId가 포함된다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com")
                member.activate()
                memberRepository.save(member)

                val redirectUrl = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val accessToken = UriComponentsBuilder.fromUriString(redirectUrl)
                    .build().queryParams["accessToken"]!!.first()
                jwtTokenProvider.getMemberId(accessToken) shouldBe member.id
            }
        }
    },
)
