package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.serialization.ObjectMapperFactory
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import java.time.Duration
import java.time.Instant

/** 애플 서버 간 알림의 payload(JWS)를 검증하고 이벤트를 꺼낸다. */
class AppleServerNotificationJwsVerifier(
    private val properties: AppleOAuthProperties,
    private val signedTokenParser: AppleSignedTokenParser,
) : AppleServerNotificationVerifier {

    override fun verify(payload: String): AppleServerNotification {
        val claims = parseClaims(payload)

        validateAudience(claims)
        validateIssuedAt(claims)

        val events = readEvents(claims)
        val subject = events.sub
        val eventType = events.type
        if (subject.isNullOrBlank() || eventType.isNullOrBlank()) {
            throw invalidNotification("events 에 sub 나 type 이 없다.")
        }

        return AppleServerNotification(
            type = AppleServerNotificationType.of(eventType),
            rawEventType = eventType,
            subject = subject,
        )
    }

    private fun parseClaims(payload: String): Claims =
        runCatching { signedTokenParser.parse(payload) }.getOrElse { exception ->
            if (exception !is JwtException) throw exception
            throw invalidNotification("서명 검증 실패: ${exception.message}")
        }

    private fun validateAudience(claims: Claims) {
        if (properties.clientIds.isEmpty()) {
            log.error { "애플 clientIds 설정이 비어 있어 서버 알림을 검증할 수 없다." }
            throw ErrorException(ErrorCode.INTERNAL_ERROR)
        }

        val audiences = claims.audience.orEmpty()
        if (properties.clientIds.none { it in audiences }) {
            throw invalidNotification("aud 불일치: $audiences")
        }
    }

    // 알림에는 만료 시각이 없을 수 있어 발급 시각으로 오래된 payload 를 거른다.
    private fun validateIssuedAt(claims: Claims) {
        val issuedAt = claims.issuedAt?.toInstant() ?: throw invalidNotification("iat 이 없다.")

        if (issuedAt.isBefore(Instant.now().minus(MAX_NOTIFICATION_AGE))) {
            throw invalidNotification("너무 오래된 알림이다. iat=$issuedAt")
        }
    }

    // 애플 예시는 JSON 문자열로 보내지만 객체로 오는 경우도 받는다. 못 읽으면 모든 알림을 잃는다.
    private fun readEvents(claims: Claims): AppleNotificationEvents {
        val events = claims[EVENTS_CLAIM]
        return runCatching {
            when (events) {
                is String -> eventsMapper.readValue<AppleNotificationEvents>(events)
                is Map<*, *> -> eventsMapper.convertValue<AppleNotificationEvents>(events)
                else -> null
            }
        }.getOrNull() ?: throw invalidNotification("events 클레임을 읽을 수 없다.")
    }

    private fun invalidNotification(reason: String): WarnException {
        log.warn { "애플 서버 알림 거부: $reason" }
        return WarnException(ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION)
    }

    private data class AppleNotificationEvents(
        val type: String? = null,
        val sub: String? = null,
    )

    companion object {
        private val log = KotlinLogging.logger {}
        private val eventsMapper = ObjectMapperFactory.create()
        private const val EVENTS_CLAIM = "events"
        private val MAX_NOTIFICATION_AGE = Duration.ofDays(1)
    }
}
