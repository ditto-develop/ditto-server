package com.ditto.infrastructure.oauth.kakao

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

class KakaoOAuthClient(
    private val properties: KakaoOAuthProperties,
    private val client: KakaoApiSender,
) : OAuthClient {

    override fun getAuthorizationUrl(): String {
        return "${AUTHORIZATION_URI}?" +
                "${OAuthConstants.PARAM_CLIENT_ID}=${properties.clientId}" +
                "&${OAuthConstants.PARAM_REDIRECT_URI}=${properties.redirectUri}" +
                "&${OAuthConstants.PARAM_RESPONSE_TYPE}=${OAuthConstants.RESPONSE_TYPE_CODE}" +
                "&${OAuthConstants.PARAM_SCOPE}=${SCOPE_ACCOUNT_EMAIL}"
    }

    override fun getAccessToken(code: String): String {
        val params = buildTokenRequestParams(code)

        return client.getToken(params).accessToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val response = client.getUserInfo("Bearer $accessToken")
        val kakaoAccount = response.kakaoAccount
        val email = kakaoAccount?.email
            ?: throw WarnException(ErrorCode.OAUTH_EMAIL_NOT_PROVIDED)

        return OAuthUserInfo(
            id = response.id.toString(),
            nickname = kakaoAccount.profile?.nickname ?: OAuthConstants.DEFAULT_NICKNAME,
            email = email,
        )
    }

    private fun buildTokenRequestParams(code: String): MultiValueMap<String, String> {
        val params = LinkedMultiValueMap<String, String>()

        params.add(OAuthConstants.PARAM_GRANT_TYPE, OAuthConstants.GRANT_TYPE_AUTHORIZATION_CODE)
        params.add(OAuthConstants.PARAM_CLIENT_ID, properties.clientId)
        params.add(OAuthConstants.PARAM_REDIRECT_URI, properties.redirectUri)
        params.add(OAuthConstants.PARAM_CODE, code)

        if (properties.clientSecret.isNotBlank()) {
            params.add(OAuthConstants.PARAM_CLIENT_SECRET, properties.clientSecret)
        }

        return params
    }

    companion object {
        private const val AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize"
        private const val SCOPE_ACCOUNT_EMAIL = "account_email"
    }
}
