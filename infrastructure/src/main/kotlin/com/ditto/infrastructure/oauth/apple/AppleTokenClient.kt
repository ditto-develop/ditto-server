package com.ditto.infrastructure.oauth.apple

/**
 * 탈퇴 때 애플 토큰을 폐기하려고 로그인 때 인가 코드를 refresh token 으로 바꿔 둔다.
 * clientId 는 코드를 받은 쪽(앱은 번들 ID, 웹은 Services ID)이고, 폐기도 같은 clientId 로 해야 한다.
 */
interface AppleTokenClient {

    /** 교환한 refresh token 을 돌려준다. 비밀값이 없어 교환을 건너뛰면 null 이다. */
    fun exchangeCode(code: String, clientId: String, redirectUri: String? = null): String?

    fun revoke(refreshToken: String, clientId: String)
}
