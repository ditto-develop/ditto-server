package com.ditto.infrastructure.fcm.firebase

import com.ditto.infrastructure.fcm.PushMessage
import com.ditto.infrastructure.fcm.PushSender
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Firebase Admin SDK 발송. `sendEachForMulticastAsync`로 호출 스레드를 잡지 않는다.
 * 메시지 조립은 [FcmMessageComposer], 결과 처리는 [DeadTokenCallback]이 맡는다.
 */
class FirebaseFcmSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushSender {

    override fun send(message: PushMessage, onDeadTokens: (List<String>) -> Unit) {
        // FCM 멀티캐스트는 호출당 토큰 500개 제한. 실제로는 회원당 기기 몇 개라 한 조각이다.
        message.tokens.chunked(MULTICAST_MAX_TOKENS).forEach { tokens ->
            val future = firebaseMessaging.sendEachForMulticastAsync(FcmMessageComposer.toMulticast(message, tokens))
            ApiFutures.addCallback(future, DeadTokenCallback(tokens, onDeadTokens), MoreExecutors.directExecutor())
        }
    }

    companion object {
        private const val MULTICAST_MAX_TOKENS = 500
    }
}
