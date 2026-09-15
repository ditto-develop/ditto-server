package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomMember
import java.time.LocalDateTime

data class ChatMessageResponse(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val messageType: ChatMessageType,
    val content: String,
    // IMAGE 메시지의 열람용 presigned GET URL. TEXT 등에서는 null.
    val imageUrl: String?,
    val createdAt: LocalDateTime,
    // 아직 읽지 않은 참여자 수. 계산 규칙: ChatMessage.unreadCountAmong
    val unreadCount: Int,
) {
    companion object {
        fun of(message: ChatMessage, imageUrl: String?, roomMembers: Collection<ChatRoomMember>): ChatMessageResponse =
            ChatMessageResponse(
                id = message.id,
                roomId = message.roomId,
                senderId = message.senderId,
                messageType = message.messageType,
                content = message.content,
                imageUrl = imageUrl,
                createdAt = message.createdAt,
                unreadCount = message.unreadCountAmong(roomMembers),
            )

        /**
         * SYSTEM 메시지 응답. 읽음 표시 대상이 아니라 unreadCount 가 항상 0 이므로 참여자 목록을 받지 않는다 —
         * 종료·이탈·투표 경로가 0 을 내려 주기 위해 멤버를 조회하지 않게 한다.
         */
        fun system(message: ChatMessage): ChatMessageResponse {
            require(message.messageType == ChatMessageType.SYSTEM) { "SYSTEM 메시지가 아닙니다: id=${message.id}" }
            return ChatMessageResponse(
                id = message.id,
                roomId = message.roomId,
                senderId = message.senderId,
                messageType = message.messageType,
                content = message.content,
                imageUrl = null,
                createdAt = message.createdAt,
                unreadCount = 0,
            )
        }
    }
}
