package com.ditto.infrastructure.oauth.kakao

import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants

class KakaoOAuthFakeClient(
    private val properties: KakaoOAuthProperties,
) : OAuthClient {

    override fun getAuthorizationUrl(): String {
        return "${AUTHORIZATION_URI}?" +
            "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}" +
            "&${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}" +
            "&${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}"
    }

    companion object {
        private const val AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
    }

    override fun getAccessToken(code: String): String {
        return "fake-access-token"
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        return OAuthUserInfo(
            id = "12345",
            nickname = "테스트유저",
        )
    }
}
