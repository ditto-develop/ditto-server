package com.ditto.infrastructure.oauth.kakao

import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialCredential
import com.ditto.infrastructure.oauth.OAuthClient
import com.ditto.infrastructure.oauth.OAuthUserInfo

/**
 * 카카오 네이티브 로그인 — SDK가 받아온 액세스 토큰으로 사용자 정보 API를 호출한다.
 * 리다이렉트 흐름과 같은 조회를 쓰므로 [OAuthClient]를 그대로 위임한다.
 */
class KakaoNativeAuthenticator(
    private val client: OAuthClient,
) : NativeSocialAuthenticator {

    override fun authenticate(credential: NativeSocialCredential): OAuthUserInfo =
        client.getUserInfo(credential.token)
}
