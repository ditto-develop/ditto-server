package com.ditto.api.auth.service

import com.ditto.api.config.FrontProperties
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialAuthenticatorFactory
import com.ditto.infrastructure.oauth.NativeSocialCredential
import com.ditto.infrastructure.oauth.OAuthClientFactory
import com.ditto.infrastructure.oauth.OAuthUserInfo
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
    private val nativeSocialAuthenticatorFactory: NativeSocialAuthenticatorFactory,
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
     * 네이티브 SDK가 이미 받아온 자격증명으로 사용자를 확인한다. 인가 코드 → 토큰 교환 단계가 없다는 점이
     * [getOAuthUserInfo]와 다르고, 확인 방법은 제공자마다 달라 [NativeSocialAuthenticator]가 맡는다
     * (카카오는 액세스 토큰으로 me API 호출, 애플은 ID 토큰 서명 검증).
     *
     * 카카오는 만료·위조 토큰에 제공자가 4xx로 답하는데, 이는 서버 잘못이 아니라 클라이언트가 보낸 값의
     * 문제라 500(INTERNAL_ERROR) 대신 [ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN]으로 바꿔 전달한다.
     * (애플은 검증기가 같은 코드로 이미 변환해 던진다.)
     */
    fun authenticateNative(
        provider: SocialProvider,
        credential: NativeSocialCredential,
    ): OAuthUserInfo {
        val authenticator = nativeSocialAuthenticatorFactory.getAuthenticator(provider)
        try {
            return authenticator.authenticate(credential)
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
