package com.ditto.infrastructure.oauth

import com.ditto.domain.socialaccount.SocialProvider

interface OAuthClient {

    companion object {
        const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        const val RESPONSE_TYPE_CODE = "code"
        const val CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded"
        const val DEFAULT_NICKNAME = "소셜 사용자"

        const val PARAM_CLIENT_ID = "client_id"
        const val PARAM_CLIENT_SECRET = "client_secret"
        const val PARAM_REDIRECT_URI = "redirect_uri"
        const val PARAM_RESPONSE_TYPE = "response_type"
        const val PARAM_GRANT_TYPE = "grant_type"
        const val PARAM_CODE = "code"
    }

    fun getProvider(): SocialProvider
    fun getAuthorizationUri(): String
    fun getClientId(): String
    fun getRedirectUri(): String

    fun getAuthorizationUrl(): String =
        "${getAuthorizationUri()}?" +
            "$PARAM_CLIENT_ID=${getClientId()}" +
            "&$PARAM_REDIRECT_URI=${getRedirectUri()}" +
            "&$PARAM_RESPONSE_TYPE=$RESPONSE_TYPE_CODE"

    fun getAccessToken(code: String): String
    fun getUserInfo(accessToken: String): OAuthUserInfo
}
