package com.ditto.infrastructure.oauth.kakao

import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo

class KakaoOAuthFakeClient(
    private val properties: KakaoOAuthProperties,
) : OAuthClient {

    override fun getAuthorizationUrl(): String {
        return "fake-authorization-url?client_id=${properties.clientId}"
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
