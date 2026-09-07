package com.ditto.api.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 애플 네이티브 로그인(앱) 요청.
 *
 * 카카오와 달리 액세스 토큰이 아니라 **ID 토큰(JWT)** 을 받는다 — 애플에는 사용자 정보 API가 없고
 * 토큰의 서명과 클레임이 곧 인증이기 때문이다.
 */
data class AppleNativeLoginRequest(

    /** `ASAuthorizationAppleIDCredential.identityToken` */
    @field:NotBlank
    val identityToken: String,

    /**
     * 앱이 만든 원본 nonce (선택, 권장). 애플에는 SHA-256 해시를 넘기고 서버가 같은 방식으로 대조해
     * 토큰 재사용을 막는다. 보내지 않으면 이 검증만 건너뛴다.
     */
    val rawNonce: String? = null,

    /**
     * 사용자 이름 (선택). 애플은 **최초 인가 1회만** 이름을 클라이언트에 주고 ID 토큰에는 담지 않는다.
     * 그때 앱이 받아 함께 보내면 저장한다. 재로그인 때는 비워 보내면 되고 기존 값이 유지된다.
     */
    @field:Size(max = 50)
    val name: String? = null,
)
