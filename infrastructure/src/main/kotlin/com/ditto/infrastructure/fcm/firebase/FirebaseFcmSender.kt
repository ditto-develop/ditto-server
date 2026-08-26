package com.ditto.infrastructure.fcm.firebase

import com.ditto.infrastructure.fcm.PushMessage
import com.ditto.infrastructure.fcm.PushSender
import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

/**
 * Firebase Admin SDK 발송. `sendEachForMulticastAsync`로 호출 스레드를 잡지 않는다.
 *
 * [PushMessage]의 알림 속성을 플랫폼별 형식으로 번역하는 곳이 여기다 —
 * `unreadCount`는 APNs 뱃지로, `ttl`은 Android `ttl`과 APNs `apns-expiration` 양쪽으로.
 *
 * Android 우선순위는 HIGH 고정 — 일반 우선순위는 Doze 모드에서 묶였다가 몰아서 배달돼
 * 채팅 알림이 늦는다. iOS 는 FCM 이 APNs 로 넘길 때 알아서 대응 우선순위를 쓴다.
 */
class FirebaseFcmSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushSender {

    override fun send(message: PushMessage, onDeadTokens: (List<String>) -> Unit) {
        // FCM 멀티캐스트는 호출당 토큰 500개 제한. 실제로는 회원당 기기 몇 개라 한 조각이다.
        message.tokens.chunked(MULTICAST_MAX_TOKENS).forEach { tokens ->
            val future = firebaseMessaging.sendEachForMulticastAsync(buildMulticast(message, tokens))
            attachResultHandler(future, tokens, onDeadTokens)
        }
    }

    private fun buildMulticast(message: PushMessage, tokens: List<String>): MulticastMessage =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(message.title)
                    .setBody(message.body)
                    .build(),
            )
            .putAllData(message.data)
            .setAndroidConfig(buildAndroidConfig(message))
            .setApnsConfig(buildApnsConfig(message))
            .build()

    private fun buildAndroidConfig(message: PushMessage): AndroidConfig {
        val builder = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
        if (message.ttl != null) {
            builder.setTtl(message.ttl.toMillis())
        }
        return builder.build()
    }

    private fun buildApnsConfig(message: PushMessage): ApnsConfig {
        val aps = Aps.builder()
        if (message.unreadCount != null) {
            aps.setBadge(message.unreadCount)
        }
        val builder = ApnsConfig.builder().setAps(aps.build())
        if (message.ttl != null) {
            // APNs 는 유효 기간을 절대 시각(epoch 초)으로 받는다.
            builder.putHeader(APNS_EXPIRATION_HEADER, Instant.now().plus(message.ttl).epochSecond.toString())
        }
        return builder.build()
    }

    /**
     * 발송 결과에서 무효 토큰(`UNREGISTERED` — 앱 삭제 등)만 골라 [onDeadTokens]로 돌려준다.
     * 응답 순서는 요청 토큰 순서와 같다. 그 외 실패는 일시적일 수 있어 로그만 남긴다.
     */
    private fun attachResultHandler(
        future: ApiFuture<BatchResponse>,
        tokens: List<String>,
        onDeadTokens: (List<String>) -> Unit,
    ) {
        ApiFutures.addCallback(
            future,
            object : ApiFutureCallback<BatchResponse> {
                override fun onSuccess(result: BatchResponse) {
                    val deadTokens = result.responses
                        .withIndex()
                        .filter { (_, response) -> response.exception?.messagingErrorCode == MessagingErrorCode.UNREGISTERED }
                        .map { (index, _) -> tokens[index] }
                    if (result.failureCount > deadTokens.size) {
                        logger.warn { "푸시 일부 실패: 실패 ${result.failureCount}건 중 무효 토큰 ${deadTokens.size}건" }
                    }
                    if (deadTokens.isNotEmpty()) {
                        onDeadTokens(deadTokens)
                    }
                }

                override fun onFailure(t: Throwable) {
                    logger.warn(t) { "푸시 발송 실패 — 무시한다: tokens=${tokens.size}개" }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    companion object {
        private const val MULTICAST_MAX_TOKENS = 500
        private const val APNS_EXPIRATION_HEADER = "apns-expiration"
        private val logger = KotlinLogging.logger {}
    }
}
