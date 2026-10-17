package com.ditto.infrastructure.oauth.apple

/** clientId 는 코드를 받은 쪽(앱은 번들 ID, 웹은 Services ID)이고, 폐기도 같은 clientId 로 해야 한다. */
interface AppleTokenClient {

    /** 비밀값이 없어 교환을 건너뛰면 null 이다. */
    fun exchangeCodeForRefreshToken(code: String, clientId: String, redirectUri: String? = null): String?

    /** 실제로 폐기했으면 true. 비밀값이 없어 건너뛰면 false 다. */
    fun revoke(refreshToken: String, clientId: String): Boolean
}
