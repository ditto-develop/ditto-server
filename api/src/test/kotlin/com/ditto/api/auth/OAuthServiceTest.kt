package com.ditto.api.auth

import com.ditto.api.auth.service.OAuthService
import com.ditto.api.config.FrontProperties
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.http.HttpStatus
import javax.sql.DataSource

/**
 * 네이티브 로그인은 클라이언트가 준 소셜 토큰을 그대로 쓰므로, 제공자의 4xx를 서버 오류로 올리면 안 된다.
 * Fake 카카오 클라이언트로는 실패를 재현할 수 없어 실패만 던지는 클라이언트를 끼워 검증한다.
 */
class OAuthServiceTest(
    private val frontProperties: FrontProperties,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        fun serviceThrowing(exception: RuntimeException): OAuthService {
            val client = object : OAuthClient {
                override fun getAuthorizationUrl(): String = throw exception
                override fun getAccessToken(code: String): String = throw exception
                override fun getUserInfo(accessToken: String): OAuthUserInfo = throw exception
            }
            return OAuthService(OAuthClientFactory(mapOf(SocialProvider.KAKAO to client)), frontProperties)
        }

        "네이티브 토큰으로 사용자 정보 조회" - {
            "제공자가 4xx를 주면 유효하지 않은 소셜 토큰으로 알린다" {
                val service = serviceThrowing(HttpClientErrorException(HttpStatus.UNAUTHORIZED))

                val exception = shouldThrow<WarnException> {
                    service.getOAuthUserInfoByAccessToken(SocialProvider.KAKAO, "expired-token")
                }
                exception.errorCode shouldBe ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN
            }

            "제공자가 5xx를 주면 그대로 서버 오류로 남긴다" {
                val service = serviceThrowing(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))

                shouldThrow<HttpServerErrorException> {
                    service.getOAuthUserInfoByAccessToken(SocialProvider.KAKAO, "any-token")
                }
            }
        }
    },
)
