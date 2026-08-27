package com.ditto.infrastructure.fcm

/**
 * 푸시 발송(FCM) 어댑터. 비동기라 호출 스레드를 잡지 않고, 결과는 콜백으로만 돌아온다.
 * 배달 보장은 없다 — FCM 이 접수했다는 것까지만 확인된다.
 */
interface PushSender {

    /**
     * [message]를 담긴 토큰 전체에 보낸다.
     *
     * @param onDeadTokens 무효 판정(`UNREGISTERED` — 앱 삭제 등)된 토큰들. 호출부가 주소록에서
     *        지워야 한다. 방치하면 실패율이 쌓여 FCM 이 프로젝트 발송량을 제한한다
     */
    fun send(message: PushMessage, onDeadTokens: (List<String>) -> Unit = {})
}
