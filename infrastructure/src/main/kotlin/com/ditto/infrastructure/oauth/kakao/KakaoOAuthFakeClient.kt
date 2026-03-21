package com.ditto.infrastructure.oauth.kakao

import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo

class KakaoOAuthFakeClient : OAuthClient {

    private val clientIdStub = ArrayDeque<String>()

    fun stubClientId(clientId: String) {
        clientIdStub.add(clientId)
    }

    override fun getProvider(): SocialProvider {
        TODO("Not yet implemented")
    }

    override fun getAuthorizationUri(): String {
        TODO("Not yet implemented")
    }

    override fun getClientId(): String {
        if (clientIdStub.isEmpty()) {
            return "clientId"
        }

        return clientIdStub.first()
    }

    override fun getRedirectUri(): String {
        TODO("Not yet implemented")
    }

    override fun getAccessToken(code: String): String {
        TODO("Not yet implemented")
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        TODO("Not yet implemented")
        return OAuthUserInfo(

        )
    }
}
