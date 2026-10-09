package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * local·test 프로파일용. 서명 없이 "이벤트타입:sub" 형식의 payload 를 그대로 읽는다.
 * 실제 서명 검증은 AppleServerNotificationJwsVerifierTest 가 맡는다.
 */
class AppleServerNotificationFakeVerifier : AppleServerNotificationVerifier {

    override fun verify(payload: String): AppleServerNotification {
        val (eventType, subject) = payload.split(DELIMITER, limit = 2)
            .takeIf { it.size == 2 && it.none(String::isBlank) }
            ?: throw WarnException(ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION)

        return AppleServerNotification(
            type = AppleServerNotificationType.of(eventType),
            eventType = eventType,
            subject = subject,
        )
    }

    companion object {
        private const val DELIMITER = ":"
    }
}
