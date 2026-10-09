package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import io.jsonwebtoken.Jwts
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * 인증 없는 엔드포인트라 서명 검증이 곧 인증이다.
 * 테스트가 만든 RSA 키로 애플처럼 서명해서 서명·발급자·aud·iat·events 를 확인한다.
 */
class AppleServerNotificationJwsVerifierTest : FreeSpec(
    {
        val bundleId = "pics.ditto.app"
        val keyId = "test-key-id"
        val subject = "001234.apple-subject.0000"
        val keyPair: KeyPair = AppleJwksFixture.newKeyPair()
        val otherKeyPair: KeyPair = AppleJwksFixture.newKeyPair()

        fun eventsJson(type: String = "consent-revoked", sub: String? = subject): String =
            if (sub == null) {
                """{"type":"$type","event_time":1760000000000}"""
            } else {
                """{"type":"$type","sub":"$sub","event_time":1760000000000}"""
            }

        fun payload(
            events: Any? = eventsJson(),
            audience: String = bundleId,
            issuer: String = AppleOAuthProperties.ISSUER,
            issuedAt: Instant? = Instant.now(),
            signWith: KeyPair = keyPair,
        ): String = Jwts.builder()
            .header().keyId(keyId).and()
            .issuer(issuer)
            .audience().add(audience).and()
            .apply {
                if (issuedAt != null) issuedAt(Date.from(issuedAt))
                if (events != null) claim("events", events)
            }
            .signWith(signWith.private as RSAPrivateKey)
            .compact()

        fun verifier(clientIds: List<String> = listOf(bundleId)): AppleServerNotificationJwsVerifier {
            val properties = AppleOAuthProperties(clientIds = clientIds)
            val sender = AppleJwksFixture.senderReturning(AppleJwksFixture.jwksJson(keyId, keyPair))
            return AppleServerNotificationJwsVerifier(properties, AppleSignedTokenParser(properties, sender))
        }

        "정상 알림" - {
            "events 의 type 과 sub 를 읽는다" {
                val notification = verifier().verify(payload())

                notification.type shouldBe AppleServerNotificationType.CONSENT_REVOKED
                notification.eventType shouldBe "consent-revoked"
                notification.subject shouldBe subject
            }

            "계정 삭제는 account-delete 와 account-deleted 둘 다 같은 타입으로 읽는다" {
                val verifier = verifier()

                verifier.verify(payload(events = eventsJson(type = "account-delete"))).type shouldBe
                    AppleServerNotificationType.ACCOUNT_DELETED
                verifier.verify(payload(events = eventsJson(type = "account-deleted"))).type shouldBe
                    AppleServerNotificationType.ACCOUNT_DELETED
            }

            "모르는 type 이면 UNKNOWN 으로 읽고 원문을 남긴다" {
                val notification = verifier().verify(payload(events = eventsJson(type = "new-event")))

                notification.type shouldBe AppleServerNotificationType.UNKNOWN
                notification.eventType shouldBe "new-event"
            }

            "events 가 문자열이 아니라 객체로 와도 읽는다" {
                val events = mapOf("type" to "email-disabled", "sub" to subject)

                val notification = verifier().verify(payload(events = events))

                notification.type shouldBe AppleServerNotificationType.EMAIL_DISABLED
                notification.subject shouldBe subject
            }

            "aud 가 허용 목록 중 하나와 맞으면 통과한다" {
                val notification = verifier(clientIds = listOf("pics.ditto.web", bundleId)).verify(payload())

                notification.subject shouldBe subject
            }
        }

        "거부해야 하는 알림" - {
            "다른 키로 서명했으면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    verifier().verify(payload(signWith = otherKeyPair))
                }
                exception.errorCode shouldBe ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION
            }

            "발급자가 애플이 아니면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    verifier().verify(payload(issuer = "https://evil.example.com"))
                }
                exception.errorCode shouldBe ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION
            }

            "aud 가 우리 앱이 아니면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    verifier().verify(payload(audience = "com.other.app"))
                }
                exception.errorCode shouldBe ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION
            }

            "iat 이 없으면 거부한다" {
                shouldThrow<WarnException> { verifier().verify(payload(issuedAt = null)) }
            }

            "하루보다 오래된 알림은 거부한다" {
                val issuedAt = Instant.now().minus(Duration.ofDays(1)).minus(Duration.ofMinutes(1))

                shouldThrow<WarnException> { verifier().verify(payload(issuedAt = issuedAt)) }
            }

            "events 클레임이 없으면 거부한다" {
                shouldThrow<WarnException> { verifier().verify(payload(events = null)) }
            }

            "events 가 JSON 이 아니면 거부한다" {
                shouldThrow<WarnException> { verifier().verify(payload(events = "not-json")) }
            }

            "events 에 sub 가 없으면 거부한다" {
                shouldThrow<WarnException> { verifier().verify(payload(events = eventsJson(sub = null))) }
            }

            "JWS 형식이 아니면 거부한다" {
                shouldThrow<WarnException> { verifier().verify("not-a-jws") }
            }

            "clientIds 설정이 비어 있으면 서버 오류로 알린다" {
                val exception = shouldThrow<ErrorException> { verifier(clientIds = emptyList()).verify(payload()) }
                exception.errorCode shouldBe ErrorCode.INTERNAL_ERROR
            }
        }
    },
)
