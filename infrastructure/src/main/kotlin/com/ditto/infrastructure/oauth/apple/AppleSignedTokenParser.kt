package com.ditto.infrastructure.oauth.apple

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import io.jsonwebtoken.security.Jwk
import io.jsonwebtoken.security.Jwks
import java.security.Key
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * 애플이 서명한 JWT 의 서명과 발급자를 확인하고 클레임을 꺼낸다. ID 토큰과 서버 알림이 같은 키 세트를 쓴다.
 * 애플은 키를 주기적으로 바꾸므로 캐시에 없는 kid 가 오면 한 번 다시 받아온다.
 * 검증에 실패하면 JwtException 을 그대로 던지고, 어떤 오류 코드로 바꿀지는 호출하는 쪽이 정한다.
 */
class AppleSignedTokenParser(
    private val properties: AppleOAuthProperties,
    private val jwksSender: AppleJwksSender,
) {
    private val cachedKeys = AtomicReference<CachedJwks?>(null)

    fun parse(token: String): Claims =
        try {
            parseWith(keys(), token)
        } catch (e: UnknownKeyIdException) {
            log.info { "애플 JWKS 캐시에 없는 kid(${e.keyId}) 라서 공개키를 다시 받아온다." }
            parseWith(fetchKeys(), token)
        }

    private fun parseWith(keys: Map<String, Key>, token: String): Claims =
        Jwts.parser()
            .keyLocator(
                object : Locator<Key> {
                    override fun locate(header: Header): Key {
                        val keyId = header["kid"] as? String
                        return keys[keyId] ?: throw UnknownKeyIdException(keyId)
                    }
                },
            )
            .requireIssuer(AppleOAuthProperties.ISSUER)
            .build()
            .parseSignedClaims(token)
            .payload

    private fun keys(): Map<String, Key> {
        val cached = cachedKeys.get()
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.keys
        }
        return fetchKeys()
    }

    private fun fetchKeys(): Map<String, Key> {
        // JwkSet 은 JSON 객체(Map)이자 키 목록(Iterable)이라 `.keys`·`mapNotNull` 이 양쪽으로 해석된다.
        // Iterable 로 타입을 못박아 키 목록 쪽으로 고정한다.
        val jwkSet: Iterable<Jwk<*>> = Jwks.setParser().build().parse(jwksSender.getKeys())
        val keys = jwkSet
            .mapNotNull { jwk -> jwk.id?.let { keyId -> keyId to jwk.toKey() } }
            .toMap()

        cachedKeys.set(CachedJwks(keys = keys, expiresAt = Instant.now().plus(properties.jwksCacheTtl)))
        return keys
    }

    private class UnknownKeyIdException(val keyId: String?) : JwtException("알 수 없는 kid: $keyId")

    private data class CachedJwks(
        val keys: Map<String, Key>,
        val expiresAt: Instant,
    )

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
