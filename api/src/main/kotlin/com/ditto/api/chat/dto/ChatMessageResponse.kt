package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
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
) {
    companion object {
        fun of(message: ChatMessage, imageUrl: String?): ChatMessageResponse = ChatMessageResponse(
            id = message.id,
            roomId = message.roomId,
            senderId = message.senderId,
            messageType = message.messageType,
            content = message.content,
            imageUrl = imageUrl,
            createdAt = message.createdAt,
        )
    }
}
