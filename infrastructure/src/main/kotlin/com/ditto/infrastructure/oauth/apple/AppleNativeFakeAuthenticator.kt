package com.ditto.infrastructure.oauth.apple

import com.ditto.infrastructure.oauth.NativeSocialAuthenticator
import com.ditto.infrastructure.oauth.NativeSocialCredential
import com.ditto.infrastructure.oauth.OAuthUserInfo
import com.ditto.infrastructure.oauth.constants.OAuthConstants

/**
 * local·test 프로파일용. 애플 서버에 붙지 않고 고정 사용자를 돌려준다
 * (실제 ID 토큰 검증은 [AppleIdTokenVerifier] 단위 테스트가 담당한다).
 */
class AppleNativeFakeAuthenticator : NativeSocialAuthenticator {

    override fun authenticate(credential: NativeSocialCredential): OAuthUserInfo = OAuthUserInfo(
        id = FAKE_SUBJECT,
        nickname = OAuthConstants.DEFAULT_NICKNAME,
        email = "apple-user@privaterelay.appleid.com",
        birthDate = null,
        // 최초 인가에서만 오는 값이라 앱이 준 값을 그대로 흘린다.
        name = credential.name,
        phoneNumber = null,
        gender = null,
    )

    companion object {
        const val FAKE_SUBJECT = "001234.fake-apple-subject.0000"
    }
}
