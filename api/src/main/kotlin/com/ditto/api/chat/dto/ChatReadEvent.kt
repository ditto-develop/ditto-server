package com.ditto.api.chat.dto

/**
 * 읽음 커서가 전진했음을 방 토픽으로 알리는 STOMP 프레임.
 * 메시지 프레임과 같은 토픽을 쓰므로 [type]으로 구분한다. 저장하지 않는다.
 * 원본은 chat_room_member.last_read_message_id 이고 재접속 시 메시지 조회의 unreadCount 로 복구된다.
 */
data class ChatReadEvent(
    val roomId: Long,
    val memberId: Long,
    // 직전 커서(처음이면 null). FE 는 previous < id <= last 구간만 1 줄인다. 구간 없이 줄이면 이전 READ 와 중복 감소.
    val previousLastReadMessageId: Long?,
    val lastReadMessageId: Long,
) {
    val type: String
        get() = TYPE

    companion object {
        const val TYPE = "READ"
    }
}
