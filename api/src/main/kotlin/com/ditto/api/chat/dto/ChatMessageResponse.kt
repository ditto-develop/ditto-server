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
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(message: ChatMessage): ChatMessageResponse = ChatMessageResponse(
            id = message.id,
            roomId = message.roomId,
            senderId = message.senderId,
            messageType = message.messageType,
            content = message.content,
            createdAt = message.createdAt,
        )
    }
}
