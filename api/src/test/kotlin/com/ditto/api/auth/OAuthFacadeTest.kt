package com.ditto.api.auth

import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.FrontProperties
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Gender
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
import java.time.LocalDateTime
import javax.sql.DataSource

class OAuthFacadeTest(
    private val oAuthFacade: OAuthFacade,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authService: AuthService,
    private val serverTimeProvider: ServerTimeProvider,
    private val sanctionExpiryService: SanctionExpiryService,
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
                    serverTimeProvider = serverTimeProvider,
                    sanctionExpiryService = sanctionExpiryService,
                )

                val exception = shouldThrow<ErrorException> {
                    facadeWithNoClients.getAuthorizationUrl(SocialProvider.KAKAO)
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }

        "소셜 로그인" - {
            "신규 사용자면 PENDING으로 생성되고 토큰과 signupRequired=true를 반환한다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["accessToken"]?.first() shouldNotBe null
                params["signupRequired"]?.first() shouldBe "true"
                result.refreshToken shouldNotBe null
                memberRepository.count() shouldBe 1
                memberRepository.findAll().first().status shouldBe MemberStatus.PENDING
                socialAccountRepository.count() shouldBe 1
                refreshTokenRepository.count() shouldBe 1
            }

            "신규 사용자면 카카오에서 받은 생년월일이 Member에 저장된다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                // KakaoOAuthFakeClient가 1995-03-15를 반환한다.
                memberRepository.findAll().first().birthDate shouldBe LocalDateTime.of(1995, 3, 15, 0, 0)
            }

            "신규 사용자면 카카오에서 받은 이름·전화번호·성별이 Member에 저장된다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                // KakaoOAuthFakeClient가 이름=테스트, 전화번호=010-1234-5678, 성별=MALE을 반환한다.
                val member = memberRepository.findAll().first()
                member.name shouldBe "테스트"
                member.phoneNumber shouldBe "010-1234-5678"
                member.gender shouldBe Gender.MALE
            }

            "PENDING 사용자가 재로그인해도 토큰과 signupRequired=true를 반환한다" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["signupRequired"]?.first() shouldBe "true"
                result.refreshToken shouldNotBe null
                memberRepository.count() shouldBe 1
            }

            "ACTIVE 사용자면 signupRequired=false와 토큰을 반환한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["accessToken"]?.first() shouldNotBe null
                params["signupRequired"]?.first() shouldBe "false"
                jwtTokenProvider.isValid(params["accessToken"]!!.first()) shouldBe true
                result.refreshToken shouldNotBe null
                refreshTokenRepository.count() shouldBe 1
            }

            "이용 정지 중인 회원이 로그인하면 토큰 없이 제재 콜백으로 안내한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.suspendUntil(LocalDateTime.now().plusDays(7))
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["sanctioned"]?.first() shouldBe "true"
                params["sanctionCode"]?.first() shouldBe "MEMBER_SUSPENDED"
                params["suspendedUntil"]?.first() shouldNotBe null
                params["accessToken"] shouldBe null
                result.refreshToken shouldBe null
                refreshTokenRepository.count() shouldBe 0
            }

            "영구 차단 회원이 로그인하면 토큰 없이 차단 콜백으로 안내한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.ban()
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["sanctionCode"]?.first() shouldBe "MEMBER_BANNED"
                params["suspendedUntil"] shouldBe null
                result.refreshToken shouldBe null
            }

            "정지 해제 예정일이 지난 회원은 정상 로그인되고 ACTIVE로 원복된다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.suspendUntil(LocalDateTime.now().minusDays(1))
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val params = UriComponentsBuilder.fromUriString(result.redirectUrl).build().queryParams
                params["accessToken"]?.first() shouldNotBe null
                result.refreshToken shouldNotBe null

                val reloaded = memberRepository.findById(member.id).orElseThrow()
                reloaded.status shouldBe MemberStatus.ACTIVE
                reloaded.suspendedUntil shouldBe null
            }

            "refreshToken은 redirect 쿼리 파라미터에 포함되지 않는다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.redirectUrl shouldNotContain "refreshToken"
            }

            "redirect URL이 설정된 프론트 콜백 URL을 포함한다" {
                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                result.redirectUrl shouldContain frontProperties.oauthCallbackUrl
            }

            "JWT에 memberId가 포함된다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                memberRepository.save(member)

                val result = oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                val accessToken = UriComponentsBuilder.fromUriString(result.redirectUrl)
                    .build().queryParams["accessToken"]!!.first()
                jwtTokenProvider.getMemberId(accessToken) shouldBe member.id
            }
        }

        "네이티브 소셜 로그인" - {
            "신규 사용자면 PENDING으로 생성되고 accessToken·signupRequired=true를 반환한다" {
                val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                result.response.accessToken shouldNotBe null
                result.response.signupRequired shouldBe true
                result.response.sanctioned shouldBe false
                result.refreshToken shouldNotBe null
                memberRepository.count() shouldBe 1
                memberRepository.findAll().first().status shouldBe MemberStatus.PENDING
                socialAccountRepository.count() shouldBe 1
                refreshTokenRepository.count() shouldBe 1
            }

            "ACTIVE 사용자면 signupRequired=false이고 accessToken에 memberId가 담긴다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                memberRepository.save(member)

                val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                result.response.signupRequired shouldBe false
                jwtTokenProvider.getMemberId(result.response.accessToken!!) shouldBe member.id
            }

            "이용 정지 중인 회원이면 토큰 없이 제재 코드와 해제 예정일을 반환한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.suspendUntil(LocalDateTime.now().plusDays(7))
                memberRepository.save(member)

                val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                result.response.accessToken shouldBe null
                result.response.sanctioned shouldBe true
                result.response.sanctionCode shouldBe ErrorCode.MEMBER_SUSPENDED.name
                result.response.suspendedUntil shouldNotBe null
                result.refreshToken shouldBe null
                refreshTokenRepository.count() shouldBe 0
            }

            "영구 차단 회원이면 해제 예정일 없이 차단 코드만 반환한다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.ban()
                memberRepository.save(member)

                val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                result.response.sanctioned shouldBe true
                result.response.sanctionCode shouldBe ErrorCode.MEMBER_BANNED.name
                result.response.suspendedUntil shouldBe null
                result.refreshToken shouldBe null
            }

            "정지 해제 예정일이 지난 회원은 정상 로그인되고 ACTIVE로 원복된다" {
                val member =
                    memberSocialAccountService.findOrCreateMember(SocialProvider.KAKAO, "12345", "test@example.com", null)
                member.activate()
                member.suspendUntil(LocalDateTime.now().minusDays(1))
                memberRepository.save(member)

                val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                result.response.sanctioned shouldBe false
                result.response.accessToken shouldNotBe null

                val reloaded = memberRepository.findById(member.id).orElseThrow()
                reloaded.status shouldBe MemberStatus.ACTIVE
                reloaded.suspendedUntil shouldBe null
            }

            "리다이렉트 로그인과 같은 회원으로 이어진다 (소셜 계정이 하나만 생긴다)" {
                oAuthFacade.login(SocialProvider.KAKAO, "auth-code")

                oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, "kakao-sdk-access-token")

                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }
        }
    },
)
