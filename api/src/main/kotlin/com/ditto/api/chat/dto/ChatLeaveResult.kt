package com.ditto.api.chat.dto

/**
 * 방 나가기의 처리 결과. 컨트롤러가 이 값으로 후속 처리를 정한다.
 * [systemMessages]는 실시간 브로드캐스트 대상이다. [isRoomEnded]면 방 전원의 평가를,
 * [hasLeftOpenRoom]이면 나간 사람 한 명의 평가를 연다. 멱등 재요청이면 모두 비어 있다.
 */
data class ChatLeaveResult(
    val systemMessages: List<ChatMessageResponse>,
    val isRoomEnded: Boolean,
    /** 열린 그룹 방에서 나갔고 방은 남아 있다. 개방 전에 나간 사람은 방이 끝날 때 평가를 받는다. */
    val hasLeftOpenRoom: Boolean = false,
) {
    companion object {
        fun nothing(): ChatLeaveResult = ChatLeaveResult(systemMessages = emptyList(), isRoomEnded = false)
    }
}
