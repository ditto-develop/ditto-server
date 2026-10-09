package com.ditto.infrastructure.oauth.apple

import java.util.concurrent.CopyOnWriteArrayList

/** local·test 프로파일용. 애플에 붙지 않고 코드로 가짜 refresh token 을 만들며, 폐기한 토큰을 기억해 테스트가 확인할 수 있게 한다. */
class AppleTokenFakeClient : AppleTokenClient {

    val revokedTokens: MutableList<String> = CopyOnWriteArrayList()

    override fun exchangeCode(code: String, clientId: String, redirectUri: String?): String =
        "$FAKE_REFRESH_TOKEN_PREFIX$code"

    override fun revoke(refreshToken: String, clientId: String): Boolean {
        revokedTokens.add(refreshToken)
        return true
    }

    companion object {
        const val FAKE_REFRESH_TOKEN_PREFIX = "fake-apple-refresh-"
    }
}
