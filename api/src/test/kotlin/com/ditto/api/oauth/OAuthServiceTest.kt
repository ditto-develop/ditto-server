package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberRepository
import com.ditto.domain.socialaccount.SocialAccountRepository
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.kakao.KakaoOAuthFakeClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class OAuthServiceTest(
    private val oAuthService: OAuthService,
    private val oAuthClientFactory: OAuthClientFactory,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "인가 URL 조회" - {
            "카카오 인가 URL을 반환한다" {
                val result = oAuthService.getAuthorizationUrl(SocialProvider.KAKAO)

                result shouldNotBe null
                result.contains("client_id=test-client-id") shouldBe true
            }

            "지원하지 않는 제공자면 예외가 발생한다" {
                val serviceWithNoClients = OAuthService(
                    oAuthClients = emptyList(),
                    memberRepository = memberRepository,
                    socialAccountRepository = socialAccountRepository,
                    jwtTokenProvider = jwtTokenProvider,
                )

                val exception = shouldThrow<WarnException> {
                    serviceWithNoClients.getAuthorizationUrl(SocialProvider.KAKAO)
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }

        "스터빙해야할 때" {
            val client = oAuthClientFactory.getClient(SocialProvider.KAKAO) as KakaoOAuthFakeClient
            client.stubClientId("test-client-id")
        }

        "소셜 로그인" - {
            "신규 사용자면 회원가입 후 JWT를 발급한다" {
                val result = oAuthService.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "기존 사용자면 기존 회원으로 JWT를 발급한다" {
                oAuthService.login(SocialProvider.KAKAO, "auth-code")
                val memberCountBefore = memberRepository.count()

                val result = oAuthService.login(SocialProvider.KAKAO, "auth-code")

                result.accessToken shouldNotBe null
                jwtTokenProvider.isValid(result.accessToken) shouldBe true
                memberRepository.count() shouldBe memberCountBefore
            }
        }
    },
)
