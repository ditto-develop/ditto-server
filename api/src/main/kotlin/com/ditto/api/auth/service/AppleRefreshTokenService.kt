package com.ditto.api.auth.service

import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.apple.AppleOAuthProperties
import com.ditto.infrastructure.oauth.apple.AppleTokenClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * 탈퇴 때 애플 토큰을 폐기하려고 로그인 때 받은 인가 코드를 애플 refresh token 으로 바꿔 둔다.
 * 우리 서비스의 refresh token 과는 다른 토큰이고 사용자에게 내려주지 않는다.
 */
@Service
class AppleRefreshTokenService(
    private val appleTokenClient: AppleTokenClient,
    private val socialAccountRepository: SocialAccountRepository,
    private val appleOAuthProperties: AppleOAuthProperties,
) {

    // 교환에 실패해도 로그인은 진행한다. 그 회원은 다음 로그인 때 다시 받는다.
    fun exchangeAndStore(userInfo: OAuthUserInfo, authorizationCode: String) {
        val clientId = userInfo.clientId ?: return
        val refreshToken = runCatching {
            appleTokenClient.exchangeCode(authorizationCode, clientId, redirectUriFor(clientId))
        }
            .onFailure { log.warn { "애플 인가 코드 교환 실패, 토큰 없이 로그인을 진행한다: ${it.message}" } }
            .getOrNull()
            ?: return

        val account = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, userInfo.id)
            ?: return
        account.storeProviderToken(refreshToken = refreshToken, clientId = clientId)
        socialAccountRepository.save(account)
    }

    // 웹(Services ID)에서 받은 코드는 인가 요청 때 쓴 redirect_uri 를 함께 보내야 교환된다.
    private fun redirectUriFor(clientId: String): String? =
        appleOAuthProperties.webRedirectUri.takeIf { clientId == appleOAuthProperties.webClientId }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
