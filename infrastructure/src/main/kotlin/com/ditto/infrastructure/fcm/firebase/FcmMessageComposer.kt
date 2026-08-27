package com.ditto.infrastructure.fcm.firebase

import com.ditto.infrastructure.fcm.PushMessage
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import java.time.Instant

/**
 * [PushMessage]의 알림 속성을 FCM 메시지로 조립한다 — 어느 재료가 어느 플랫폼 블록에 놓이는지가
 * 여기 있다: `unreadCount`는 APNs 뱃지로, `ttl`은 Android `ttl`과 APNs `apns-expiration` 양쪽으로.
 *
 * Android 우선순위는 HIGH 고정 — 일반 우선순위는 Doze 모드에서 묶였다가 몰아서 배달돼
 * 채팅 알림이 늦는다. iOS 는 FCM 이 APNs 로 넘길 때 알아서 대응 우선순위를 쓴다.
 */
internal object FcmMessageComposer {

    fun toMulticast(message: PushMessage, tokens: List<String>): MulticastMessage =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(message.title)
                    .setBody(message.body)
                    .build(),
            )
            .putAllData(message.data)
            .setAndroidConfig(androidConfigOf(message))
            .setApnsConfig(apnsConfigOf(message))
            .build()

    private fun androidConfigOf(message: PushMessage): AndroidConfig =
        AndroidConfig.builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .apply { message.ttl?.let { setTtl(it.toMillis()) } }
            .build()

    private fun apnsConfigOf(message: PushMessage): ApnsConfig =
        ApnsConfig.builder()
            .setAps(
                Aps.builder()
                    .apply { message.unreadCount?.let { setBadge(it) } }
                    .build(),
            )
            .apply {
                // APNs 는 유효 기간을 절대 시각(epoch 초)으로 받는다.
                message.ttl?.let { putHeader(APNS_EXPIRATION_HEADER, Instant.now().plus(it).epochSecond.toString()) }
            }
            .build()

    private const val APNS_EXPIRATION_HEADER = "apns-expiration"
}
