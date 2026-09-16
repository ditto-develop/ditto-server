package com.ditto.infrastructure.fcm

import java.time.Duration

/**
 * 푸시 한 건. [tokens]가 여러 개인 것은 한 회원이 기기를 여러 개 가질 수 있어서다.
 *
 * 필드는 전부 알림의 속성이다 — 플랫폼별 형식(badge·apns-expiration 등)으로의 번역은
 * 발송 구현이 맡으므로, 부르는 쪽은 플랫폼을 의식하지 않는다.
 *
 * [data]는 FCM 규격상 문자열 → 문자열이다. 숫자·불리언을 실으려면 문자열로 바꿔서 넣는다.
 * 예약 키(`from`, `message_type`, `google.`·`gcm.` 접두)는 넣지 않는다.
 */
data class PushMessage(
    val tokens: List<String>,
    val title: String,
    val body: String?,
    val data: Map<String, String> = emptyMap(),
    /** 수신자의 안읽은 알림 수. 앱 아이콘 뱃지를 지원하는 플랫폼(iOS)만 소비한다. null 이면 뱃지를 건드리지 않는다. */
    val unreadCount: Int? = null,
    /** 배달 유효 시간. 지나면 FCM 이 버린다. null 이면 기본(4주) — 시효가 있는 알림(채팅 등)은 짧게 준다. */
    val ttl: Duration? = null,
) {

    /** 발송 실패 로그가 "무슨 알림이었나"를 적을 때 쓴다. 앱도 같은 값을 [data]에서 읽어 화면을 고른다. */
    val notificationType: String?
        get() = data[DATA_KEY_TYPE]

    companion object {
        /** 알림 유형을 싣는 [data] 키. 넣는 쪽과 읽는 쪽이 모듈을 넘어 갈리므로 여기 한 곳에 둔다. */
        const val DATA_KEY_TYPE = "type"
    }
}
