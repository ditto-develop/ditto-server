package com.ditto.infrastructure.oauth.apple

import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialCredential
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants

/**
 * 애플 네이티브 로그인 — 앱이 받아온 ID 토큰을 검증해 사용자 정보를 만든다.
 *
 * 애플이 주는 것은 식별자와 (동의 시) 이메일뿐이다. 성별·나이·전화번호는 애초에 제공하지 않으므로
 * 카카오 일반 앱과 마찬가지로 온보딩에서 받는다(ADR 0021).
 *
 * **이름은 최초 인가 1회만** 클라이언트에 전달되고 ID 토큰에는 들어 있지 않다. 그래서 앱이 그때 받아
 * [NativeSocialCredential.name]으로 실어 보내며, 재로그인 시에는 null이라 기존 값이 유지된다.
 */
class AppleNativeAuthenticator(
    private val verifier: AppleIdTokenVerifier,
) : NativeSocialAuthenticator {

    override fun authenticate(credential: NativeSocialCredential): OAuthUserInfo {
        val payload = verifier.verify(idToken = credential.token, rawNonce = credential.rawNonce)

        return OAuthUserInfo(
            id = payload.subject,
            nickname = OAuthConstants.DEFAULT_NICKNAME,
            email = payload.email,
            birthDate = null,
            name = credential.name,
            phoneNumber = null,
            gender = null,
        )
    }
}
