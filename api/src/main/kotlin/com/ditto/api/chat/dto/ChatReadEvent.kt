package com.ditto.api.chat.dto

/**
 * 참여자의 읽음 커서가 **전진했음**을 방 토픽(`/sub/chat/rooms/{roomId}`)으로 알리는 STOMP 프레임.
 *
 * 메시지 프레임([ChatMessageResponse])과 같은 토픽을 타므로 [type]으로 구분한다 — FE 는 이 값이 없는
 * 프레임을 메시지로 파싱한다. 저장하지 않는다 — 읽음의 원본은 `chat_room_member.last_read_message_id`이고,
 * 재접속하면 메시지 조회의 `unreadCount`가 최신 상태를 준다.
 */
data class ChatReadEvent(
    val roomId: Long,
    val memberId: Long,
    // 직전 커서(처음 읽음이면 null). 수신 측은 previous < id <= last 구간의 unreadCount 만 1 줄인다 —
    // 구간을 안 두고 id <= last 전체를 줄이면 앞서 받은 READ 로 이미 줄인 메시지가 다시 줄어든다.
    val previousLastReadMessageId: Long?,
    val lastReadMessageId: Long,
) {
    val type: String
        get() = TYPE

    companion object {
        const val TYPE = "READ"
    }
}
