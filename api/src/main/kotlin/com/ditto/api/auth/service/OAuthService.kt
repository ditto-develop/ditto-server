package com.ditto.api.auth.service

import com.ditto.api.config.FrontProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
    private val frontProperties: FrontProperties,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthClientFactory.getClient(provider).getAuthorizationUrl()

    fun getOAuthUserInfo(
        provider: SocialProvider,
        code: String,
    ): OAuthUserInfo {
        val client = oAuthClientFactory.getClient(provider)
        val accessToken = client.getAccessToken(code)
        return client.getUserInfo(accessToken)
    }

    /**
     * 네이티브 SDK가 이미 발급받은 액세스 토큰으로 사용자 정보를 조회한다.
     * 인가 코드 → 토큰 교환 단계가 없다는 점만 [getOAuthUserInfo]와 다르다.
     *
     * 만료·위조 토큰이면 제공자가 4xx로 답하는데, 이는 서버 잘못이 아니라 클라이언트가 보낸 값의 문제라
     * 500(INTERNAL_ERROR) 대신 [ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN]으로 바꿔 전달한다.
     */
    fun getOAuthUserInfoByAccessToken(
        provider: SocialProvider,
        accessToken: String,
    ): OAuthUserInfo {
        val client = oAuthClientFactory.getClient(provider)
        try {
            return client.getUserInfo(accessToken)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.is4xxClientError) {
                throw WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
            }
            throw e
        }
    }

    fun getAuthCallbackUrl(
        accessToken: String,
        signupRequired: Boolean,
    ): String =
        UriComponentsBuilder
            .fromUriString(frontProperties.oauthCallbackUrl)
            .queryParam("accessToken", accessToken)
            .queryParam("signupRequired", signupRequired)
            .build()
            .toUriString()

    /**
     * 제재 회원의 로그인 콜백 URL — 토큰 없이 제재 사실만 전달한다.
     * FE 계약: sanctioned=true + sanctionCode(MEMBER_SUSPENDED|MEMBER_BANNED) + suspendedUntil(정지만, ISO-8601)
     */
    fun getSanctionCallbackUrl(
        sanctionCode: String,
        suspendedUntil: LocalDateTime?,
    ): String {
        val builder = UriComponentsBuilder
            .fromUriString(frontProperties.oauthCallbackUrl)
            .queryParam("sanctioned", true)
            .queryParam("sanctionCode", sanctionCode)
        if (suspendedUntil != null) {
            builder.queryParam("suspendedUntil", suspendedUntil)
        }
        return builder.build().toUriString()
    }
}
