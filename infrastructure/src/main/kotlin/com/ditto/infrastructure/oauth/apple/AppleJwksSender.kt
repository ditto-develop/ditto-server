package com.ditto.infrastructure.oauth.apple

import org.springframework.web.service.annotation.GetExchange

/**
 * 애플 공개키(JWKS) 조회. ID 토큰 서명 검증에 쓴다.
 * 응답은 JWK Set JSON 그대로 받아 jjwt 가 파싱한다.
 */
interface AppleJwksSender {

    @GetExchange(url = "https://appleid.apple.com/auth/keys")
    fun getKeys(): String
}
