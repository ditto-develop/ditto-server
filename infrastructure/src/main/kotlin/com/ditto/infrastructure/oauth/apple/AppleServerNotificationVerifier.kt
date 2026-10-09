package com.ditto.infrastructure.oauth.apple

interface AppleServerNotificationVerifier {

    fun verify(payload: String): AppleServerNotification
}

/** eventType 은 애플이 보낸 원문이다. 모르는 타입이 와도 로그에 남길 수 있게 둔다. */
data class AppleServerNotification(
    val type: AppleServerNotificationType,
    val eventType: String,
    val subject: String,
)

enum class AppleServerNotificationType(private vararg val eventTypes: String) {
    CONSENT_REVOKED("consent-revoked"),

    // 애플 문서와 외부 자료에 두 표기가 섞여 있어 둘 다 받는다.
    ACCOUNT_DELETED("account-delete", "account-deleted"),
    EMAIL_DISABLED("email-disabled"),
    EMAIL_ENABLED("email-enabled"),
    UNKNOWN,
    ;

    companion object {
        fun of(eventType: String): AppleServerNotificationType =
            entries.firstOrNull { eventType in it.eventTypes } ?: UNKNOWN
    }
}
