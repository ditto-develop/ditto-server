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
    // 안 읽은 참여자 수. 규칙은 ChatMessage.unreadCountAmong
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

        /** SYSTEM 메시지는 unreadCount 가 항상 0 이라 참여자 목록이 필요 없다. */
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
