package com.ditto.infrastructure.fcm

/**
 * 푸시 발송(FCM) 어댑터. 비동기라 호출 스레드를 잡지 않고, 결과는 콜백으로만 돌아온다.
 * 배달 보장은 없다 — FCM 이 접수했다는 것까지만 확인된다.
 */
interface PushSender {

    /**
     * [message]를 담긴 토큰 전체에 보낸다. [onResult]는 FCM 응답이 올 때 불리고, 토큰이 500개를 넘으면 나눠 보낸 조각마다 불린다.
     * 무효 토큰을 방치하면 실패율이 쌓여 FCM 이 프로젝트 발송량을 제한하므로 호출부가 지워야 한다.
     */
    fun send(message: PushMessage, onResult: (PushSendResult) -> Unit = {})
}
