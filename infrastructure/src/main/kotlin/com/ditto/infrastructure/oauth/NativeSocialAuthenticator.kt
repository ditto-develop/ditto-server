package com.ditto.infrastructure.oauth

/**
 * 네이티브 SDK가 이미 받아온 자격증명으로 사용자를 확인한다.
 *
 * [OAuthClient]와 분리한 이유: 그쪽은 "인가 URL → 코드 → 토큰 → userinfo API"라는 카카오의 리다이렉트 흐름에
 * 맞춘 계약이다. 애플 네이티브는 코드 교환도 userinfo API도 없고 **ID 토큰(JWT)을 직접 검증**해 클레임에서
 * 사용자 정보를 읽는다. 억지로 한 인터페이스에 넣으면 제공자마다 미지원 메서드가 생긴다.
 */
interface NativeSocialAuthenticator {
    fun authenticate(credential: NativeSocialCredential): OAuthUserInfo
}

/**
 * 앱이 소셜 SDK에서 받아 서버로 넘기는 값.
 *
 * @property token 카카오는 액세스 토큰, 애플은 ID 토큰(JWT).
 * @property rawNonce 애플 전용(선택) — 앱이 만든 원본 nonce. 주면 ID 토큰의 `nonce` 클레임과 대조해 재생 공격을 막는다.
 * @property name 애플 전용(선택) — 애플은 **최초 인가 1회만** 이름을 클라이언트에 주므로, 그때 앱이 받아 함께 보낸다.
 */
data class NativeSocialCredential(
    val token: String,
    val rawNonce: String? = null,
    val name: String? = null,
)
