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
    // 이 메시지를 아직 읽지 않은, 발신자를 뺀 현재 참여자 수. 0 이면 모두 읽었다. 규칙: ChatMessage.unreadCountAmong
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
    }
}
