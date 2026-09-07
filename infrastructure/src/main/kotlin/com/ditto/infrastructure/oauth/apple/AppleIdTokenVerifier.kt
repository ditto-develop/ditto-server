package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Header
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Locator
import io.jsonwebtoken.security.Jwk
import io.jsonwebtoken.security.Jwks
import java.security.Key
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * 애플 ID 토큰(JWT) 검증기.
 *
 * 애플 네이티브 로그인에는 사용자 정보 API가 없다 — 앱이 건네준 ID 토큰의 **서명과 클레임이 곧 인증**이다.
 * 그래서 아래를 모두 확인하며, 하나라도 어긋나면 클라이언트가 보낸 값의 문제이므로 4xx로 돌려준다.
 *
 * 1. 서명 — 애플 JWKS([AppleJwksSender])의 공개키로 검증한다. 애플은 키를 주기적으로 교체하므로
 *    캐시에 없는 `kid`가 오면 한 번 다시 받아온다(교체 직후의 정상 요청을 실패로 만들지 않기 위해).
 * 2. `iss` — `https://appleid.apple.com`
 * 3. `aud` — 설정한 클라이언트 ID(네이티브는 앱 번들 ID) 중 하나와 일치
 * 4. `exp` — 만료 여부(jjwt 가 파싱 단계에서 확인)
 * 5. `nonce` — 앱이 원본 nonce 를 함께 보냈을 때만. 애플에는 SHA-256 해시를 넘기므로 같은 방식으로 비교한다.
 *
 * 인가 코드 교환은 하지 않으므로 클라이언트 시크릿(.p8 키로 서명한 JWT)이 필요 없다.
 */
class AppleIdTokenVerifier(
    private val properties: AppleOAuthProperties,
    private val jwksSender: AppleJwksSender,
) {
    private val cachedKeys = AtomicReference<CachedJwks?>(null)

    fun verify(idToken: String, rawNonce: String? = null): AppleIdTokenPayload {
        val claims = parseClaims(idToken)

        verifyAudience(claims)
        verifyNonce(claims, rawNonce)

        val subject = claims.subject
        if (subject.isNullOrBlank()) {
            log.warn { "애플 ID 토큰에 sub 클레임이 없다." }
            throw WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
        }

        return AppleIdTokenPayload(
            subject = subject,
            email = claims["email"] as? String,
            isPrivateEmail = claims["is_private_email"].toBooleanClaim(),
        )
    }

    private fun parseClaims(idToken: String): Claims {
        try {
            return parseWith(refresh = false, idToken = idToken)
        } catch (e: UnknownKeyIdException) {
            // 애플이 서명 키를 교체한 직후다. 캐시를 버리고 한 번만 다시 시도한다.
            log.info { "애플 JWKS 캐시에 없는 kid(${e.keyId}) — 공개키를 다시 받아온다." }
            return retryAfterRefresh(idToken)
        } catch (e: JwtException) {
            // 서명 불일치·만료·발급자 불일치 등 — 전부 클라이언트가 보낸 토큰의 문제다.
            throw invalidToken(e)
        }
    }

    private fun retryAfterRefresh(idToken: String): Claims {
        try {
            return parseWith(refresh = true, idToken = idToken)
        } catch (e: JwtException) {
            throw invalidToken(e)
        }
    }

    private fun parseWith(refresh: Boolean, idToken: String): Claims {
        val keys = if (refresh) fetchKeys() else keys()
        return Jwts.parser()
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
            .parseSignedClaims(idToken)
            .payload
    }

    /**
     * `aud`는 여러 값일 수 있어 jjwt 의 `requireAudience` 대신 직접 대조한다 —
     * 앱(번들 ID)과 웹(Services ID)이 같은 애플 앱을 공유할 수 있기 때문이다.
     */
    private fun verifyAudience(claims: Claims) {
        val audiences = claims.audience.orEmpty()
        if (properties.clientIds.none { it in audiences }) {
            log.warn { "애플 ID 토큰의 aud 불일치: $audiences" }
            throw WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
        }
    }

    /**
     * 앱이 원본 nonce 를 보낸 경우에만 검증한다. 애플에는 원본이 아니라 SHA-256 해시를 넘기므로
     * 같은 방식으로 해시해 비교한다. nonce 없이 만든 토큰에는 클레임 자체가 없다.
     */
    private fun verifyNonce(claims: Claims, rawNonce: String?) {
        if (rawNonce == null) return

        val expected = sha256Hex(rawNonce)
        if (claims["nonce"] as? String != expected) {
            log.warn { "애플 ID 토큰의 nonce 불일치." }
            throw WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
        }
    }

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

    private fun invalidToken(cause: Exception): WarnException {
        log.warn { "애플 ID 토큰 검증 실패: ${cause.message}" }
        return WarnException(ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN)
    }

    /** 애플은 boolean 클레임을 문자열("true")로 주기도 한다. */
    private fun Any?.toBooleanClaim(): Boolean = when (this) {
        is Boolean -> this
        is String -> this.toBoolean()
        else -> false
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private class UnknownKeyIdException(val keyId: String?) : JwtException("알 수 없는 kid: $keyId")

    private data class CachedJwks(
        val keys: Map<String, Key>,
        val expiresAt: Instant,
    )

    companion object {
        private val log = KotlinLogging.logger {}
    }
}

/**
 * 검증을 통과한 애플 ID 토큰에서 읽은 값.
 *
 * @property subject 애플이 부여한 사용자 식별자. 앱(팀) 단위로 안정적이라 소셜 계정 키로 쓴다.
 * @property email 없을 수 있다. 사용자가 가리기를 택하면 애플의 릴레이 주소(`@privaterelay.appleid.com`)가 온다.
 * @property isPrivateEmail 릴레이 주소 여부.
 */
data class AppleIdTokenPayload(
    val subject: String,
    val email: String?,
    val isPrivateEmail: Boolean,
)
