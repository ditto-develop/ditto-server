package com.ditto.infrastructure.oauth

/**
 * 인가 코드를 토큰으로 바꾸고 사용자 정보 API를 호출하는, 카카오식 리다이렉트 흐름의 계약.
 * 애플은 이 흐름을 쓰지 않는다 — [SocialAuthorizationUrlProvider]와 [NativeSocialAuthenticator]만 구현한다.
 */
interface OAuthClient : SocialAuthorizationUrlProvider {

    fun getAccessToken(code: String): String
    fun getUserInfo(accessToken: String): OAuthUserInfo
}
