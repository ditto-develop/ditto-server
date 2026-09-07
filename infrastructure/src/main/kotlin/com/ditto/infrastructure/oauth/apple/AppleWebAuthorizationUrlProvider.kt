package com.ditto.infrastructure.oauth.apple

import com.ditto.infrastructure.oauth.SocialAuthorizationUrlProvider
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import org.springframework.web.util.UriComponentsBuilder

/**
 * 애플 웹 로그인의 인가 URL.
 *
 * 카카오와 두 가지가 다르다.
 * - `client_id`는 앱 번들 ID가 아니라 **Services ID**다(웹은 별도 식별자를 쓴다).
 * - `scope`(name·email)를 요청하려면 **`response_mode=form_post`가 필수**이고, 그래서 콜백이 POST로 온다.
 *
 * `response_type`에 `id_token`을 함께 요청해 콜백에서 ID 토큰을 바로 받는다 —
 * 인가 코드 교환을 하지 않으므로 클라이언트 시크릿(.p8 키)이 필요 없다.
 *
 * ⚠️ 애플은 **최초 인가 때 요청한 scope 만큼만** 이후에도 준다. 처음에 email 없이 받으면
 * 나중에 scope를 넓혀도 그 사용자의 이메일은 영영 받을 수 없다.
 */
class AppleWebAuthorizationUrlProvider(
    private val properties: AppleOAuthProperties,
) : SocialAuthorizationUrlProvider {

    override fun getAuthorizationUrl(): String =
        UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
            .queryParam(OAuthConstants.PARAM_CLIENT_ID, properties.webClientId)
            .queryParam(OAuthConstants.PARAM_REDIRECT_URI, properties.webRedirectUri)
            .queryParam(OAuthConstants.PARAM_RESPONSE_TYPE, RESPONSE_TYPE)
            .queryParam(OAuthConstants.PARAM_SCOPE, SCOPES)
            .queryParam(PARAM_RESPONSE_MODE, RESPONSE_MODE_FORM_POST)
            .build()
            .encode()
            .toUriString()

    companion object {
        private const val AUTHORIZATION_URI = "https://appleid.apple.com/auth/authorize"

        /** 코드와 함께 ID 토큰을 받아 그 자리에서 검증한다. */
        private const val RESPONSE_TYPE = "code id_token"
        private const val SCOPES = "name email"
        private const val PARAM_RESPONSE_MODE = "response_mode"
        private const val RESPONSE_MODE_FORM_POST = "form_post"
    }
}
