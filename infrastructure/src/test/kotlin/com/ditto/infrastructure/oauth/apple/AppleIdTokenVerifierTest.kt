package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.jsonwebtoken.Jwts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.util.Base64
import java.util.Date

/**
 * 애플 ID 토큰 검증은 "앱이 준 JWT 를 믿을 수 있는가"를 판정하는 인증의 핵심이라,
 * 실제 RSA 키로 토큰을 서명해 서명·발급자·`aud`·만료·nonce 를 각각 확인한다.
 * 애플 JWKS 는 이 테스트가 만든 공개키를 돌려주도록 대체한다.
 */
class AppleIdTokenVerifierTest : FreeSpec(
    {
        val bundleId = "pics.ditto.app"
        val keyId = "test-key-id"
        val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val otherKeyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        // JWKS 는 애플이 주는 형식 그대로 손으로 만든다 (Jwk 객체의 toString 은 JSON 이 아니다).
        fun jwksJson(id: String, pair: KeyPair): String {
            val publicKey = pair.public as RSAPublicKey
            fun encode(value: java.math.BigInteger): String {
                val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
                return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            }
            return """{"keys":[{"kty":"RSA","kid":"$id","use":"sig","alg":"RS256",""" +
                """"n":"${encode(publicKey.modulus)}","e":"${encode(publicKey.publicExponent)}"}]}"""
        }

        fun idToken(
            subject: String = "001234.apple-subject.0000",
            audience: String = bundleId,
            issuer: String = AppleOAuthProperties.ISSUER,
            expiresAt: Date = Date(System.currentTimeMillis() + 600_000),
            nonce: String? = null,
            email: String? = "user@privaterelay.appleid.com",
            signWith: KeyPair = keyPair,
            kid: String = keyId,
        ): String = Jwts.builder()
            .header().keyId(kid).and()
            .issuer(issuer)
            .subject(subject)
            .audience().add(audience).and()
            .expiration(expiresAt)
            .issuedAt(Date())
            .apply {
                if (nonce != null) claim("nonce", nonce)
                if (email != null) claim("email", email)
                claim("is_private_email", "true")
            }
            .signWith(signWith.private as RSAPrivateKey)
            .compact()

        fun verifier(
            sender: AppleJwksSender,
            clientIds: List<String> = listOf(bundleId),
            cacheTtl: Duration = Duration.ofHours(6),
        ) = AppleIdTokenVerifier(
            properties = AppleOAuthProperties(clientIds = clientIds, jwksCacheTtl = cacheTtl),
            jwksSender = sender,
        )

        fun senderReturning(vararg responses: String): AppleJwksSender {
            val sender = mockk<AppleJwksSender>()
            every { sender.getKeys() } returnsMany responses.toList()
            return sender
        }

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        "정상 토큰" - {
            "서명·발급자·aud 가 맞으면 sub 와 이메일을 읽는다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                val payload = verifier.verify(idToken())

                payload.subject shouldBe "001234.apple-subject.0000"
                payload.email shouldBe "user@privaterelay.appleid.com"
                payload.isPrivateEmail shouldBe true
            }

            "이메일 제공에 동의하지 않으면 이메일이 null 이다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                val payload = verifier.verify(idToken(email = null))

                payload.email shouldBe null
            }

            "aud 를 여러 개 허용하면 그중 하나만 맞아도 통과한다 (앱·웹이 한 애플 앱을 공유)" {
                val verifier = verifier(
                    senderReturning(jwksJson(keyId, keyPair)),
                    clientIds = listOf("pics.ditto.web", bundleId),
                )

                verifier.verify(idToken()).subject shouldBe "001234.apple-subject.0000"
            }
        }

        "거부해야 하는 토큰" - {
            "다른 키로 서명했으면 거부한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                val exception = shouldThrow<WarnException> {
                    verifier.verify(idToken(signWith = otherKeyPair))
                }
                exception.errorCode shouldBe ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN
            }

            "발급자가 애플이 아니면 거부한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                shouldThrow<WarnException> {
                    verifier.verify(idToken(issuer = "https://evil.example.com"))
                }
            }

            "aud 가 우리 앱이 아니면 거부한다 (다른 앱에서 발급된 토큰)" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                val exception = shouldThrow<WarnException> {
                    verifier.verify(idToken(audience = "com.other.app"))
                }
                exception.errorCode shouldBe ErrorCode.INVALID_SOCIAL_ACCESS_TOKEN
            }

            "만료된 토큰은 거부한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                shouldThrow<WarnException> {
                    verifier.verify(idToken(expiresAt = Date(System.currentTimeMillis() - 60_000)))
                }
            }

            "JWT 형식이 아니면 거부한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                shouldThrow<WarnException> { verifier.verify("not-a-jwt") }
            }
        }

        "nonce" - {
            "원본 nonce 를 주면 해시가 일치할 때만 통과한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))
                val rawNonce = "client-generated-nonce"

                val payload = verifier.verify(idToken(nonce = sha256Hex(rawNonce)), rawNonce = rawNonce)

                payload.subject shouldBe "001234.apple-subject.0000"
            }

            "원본 nonce 와 토큰의 nonce 가 다르면 거부한다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                shouldThrow<WarnException> {
                    verifier.verify(idToken(nonce = sha256Hex("다른-nonce")), rawNonce = "client-generated-nonce")
                }
            }

            "원본 nonce 를 주지 않으면 nonce 검증을 건너뛴다" {
                val verifier = verifier(senderReturning(jwksJson(keyId, keyPair)))

                verifier.verify(idToken(nonce = sha256Hex("무엇이든"))).subject shouldBe "001234.apple-subject.0000"
            }
        }

        "공개키 캐시" - {
            "캐시가 살아 있으면 JWKS 를 다시 받지 않는다" {
                val sender = senderReturning(jwksJson(keyId, keyPair))
                val verifier = verifier(sender)

                verifier.verify(idToken())
                verifier.verify(idToken())

                verify(exactly = 1) { sender.getKeys() }
            }

            "캐시에 없는 kid 가 오면 키 교체로 보고 한 번 다시 받아온다" {
                val rotatedKeyId = "rotated-key-id"
                val sender = senderReturning(
                    jwksJson(keyId, keyPair),
                    jwksJson(rotatedKeyId, otherKeyPair),
                )
                val verifier = verifier(sender)

                // 첫 호출로 옛 키를 캐시한 뒤, 새 키로 서명된 토큰이 들어온다.
                verifier.verify(idToken())
                val payload = verifier.verify(idToken(signWith = otherKeyPair, kid = rotatedKeyId))

                payload.subject shouldBe "001234.apple-subject.0000"
                verify(exactly = 2) { sender.getKeys() }
            }

            "다시 받아와도 없는 kid 면 거부한다" {
                val sender = senderReturning(jwksJson(keyId, keyPair), jwksJson(keyId, keyPair))
                val verifier = verifier(sender)

                shouldThrow<WarnException> {
                    verifier.verify(idToken(signWith = otherKeyPair, kid = "unknown-kid"))
                }
            }

            "캐시 시간이 지나면 다시 받아온다" {
                val sender = senderReturning(jwksJson(keyId, keyPair), jwksJson(keyId, keyPair))
                val verifier = verifier(sender, cacheTtl = Duration.ZERO)

                verifier.verify(idToken())
                verifier.verify(idToken())

                verify(exactly = 2) { sender.getKeys() }
            }
        }
    },
)
