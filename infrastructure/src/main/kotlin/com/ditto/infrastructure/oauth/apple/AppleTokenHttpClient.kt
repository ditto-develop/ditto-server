package com.ditto.infrastructure.oauth.apple

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

/** 애플 실패 응답은 RestClientResponseException 으로 그대로 던진다. 실패해도 로그인·탈퇴를 진행할지는 부르는 쪽이 정한다. */
class AppleTokenHttpClient(
    private val properties: AppleOAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val authSender: AppleAuthSender,
) : AppleTokenClient {

    override fun exchangeCodeForRefreshToken(code: String, clientId: String, redirectUri: String?): String? {
        if (!properties.canSignClientSecret()) {
            log.warn { "애플 client_secret 설정이 없어 인가 코드 교환을 건너뛴다." }
            return null
        }

        val params = clientParams(clientId).apply {
            add("grant_type", "authorization_code")
            add("code", code)
            redirectUri?.let { add("redirect_uri", it) }
        }
        return authSender.getToken(params).refreshToken
    }

    override fun revoke(refreshToken: String, clientId: String): Boolean {
        if (!properties.canSignClientSecret()) {
            log.warn { "애플 client_secret 설정이 없어 토큰 폐기를 건너뛴다." }
            return false
        }

        val params = clientParams(clientId).apply {
            add("token", refreshToken)
            add("token_type_hint", "refresh_token")
        }
        authSender.revoke(params)
        return true
    }

    private fun clientParams(clientId: String): MultiValueMap<String, String> =
        LinkedMultiValueMap<String, String>().apply {
            add("client_id", clientId)
            add("client_secret", clientSecretGenerator.generate(clientId))
        }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
