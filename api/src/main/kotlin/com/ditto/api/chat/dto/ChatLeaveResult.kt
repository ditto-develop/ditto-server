package com.ditto.api.chat.dto

/**
 * 방 나가기의 처리 결과. 컨트롤러가 이 값으로 후속 처리를 정한다 —
 * [systemMessages]는 실시간 브로드캐스트 대상이고, [isRoomEnded]가 참이면 평가 열기로 이어진다.
 * 멱등 재요청(이미 나갔거나 이미 끝난 방)이면 둘 다 비어 있다.
 */
data class ChatLeaveResult(
    val systemMessages: List<ChatMessageResponse>,
    val isRoomEnded: Boolean,
) {
    companion object {
        fun nothing(): ChatLeaveResult = ChatLeaveResult(systemMessages = emptyList(), isRoomEnded = false)
    }
}
