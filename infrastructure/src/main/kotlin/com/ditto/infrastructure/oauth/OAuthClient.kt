package com.ditto.infrastructure.oauth

interface OAuthClient {

    fun getAuthorizationUrl(): String
    fun getAccessToken(code: String): String
    fun getUserInfo(accessToken: String): OAuthUserInfo
}
