package com.ditto.infrastructure.fcm

/**
 * 푸시 발송(FCM) 어댑터.
 *
 * 발송은 비동기다 — 호출 스레드(STOMP 핸들러 등)를 잡지 않고, 결과는 콜백으로만 돌아온다.
 * 배달 보장은 없다. FCM 이 접수했다는 것까지만 확인되고, 폰 도착 여부는 알 수 없다.
 */
interface PushSender {

    /**
     * [message]를 담긴 토큰 전체에 보낸다.
     *
     * @param onDeadTokens 발송 결과에서 무효 판정(`UNREGISTERED`)된 토큰들.
     *        기기에서 앱이 삭제된 경우 등이며, 호출부가 주소록에서 지워야 한다 —
     *        방치하면 실패율이 쌓여 FCM 이 프로젝트 발송량을 제한한다
     */
    fun send(message: PushMessage, onDeadTokens: (List<String>) -> Unit = {})
}
