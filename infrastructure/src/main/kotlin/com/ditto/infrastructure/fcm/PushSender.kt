package com.ditto.infrastructure.fcm

/**
 * 푸시 발송(FCM) 어댑터. 비동기라 호출 스레드를 잡지 않고, 결과는 콜백으로만 돌아온다.
 * 배달 보장은 없다 — FCM 이 접수했다는 것까지만 확인된다.
 */
interface PushSender {

    /**
     * 메시지의 토큰 전체에 보내고, FCM 응답이 오면 onResult 를 부른다.
     * 토큰이 500개를 넘으면 나눠 보낸 조각마다 부른다. 호출부는 회원 한 명의 기기에 보내므로 한 번 온다고 가정한다.
     */
    fun send(message: PushMessage, onResult: (PushSendResult) -> Unit = {})
}
