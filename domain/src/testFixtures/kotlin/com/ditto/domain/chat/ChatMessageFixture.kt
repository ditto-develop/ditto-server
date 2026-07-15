package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.withId

object ChatMessageFixture {

    fun create(
        roomId: Long = 1L,
        senderId: Long = 1L,
        content: String = "안녕하세요",
        messageType: ChatMessageType = ChatMessageType.TEXT,
        id: Long = 0L,
    ): ChatMessage = ChatMessage.of(
        roomId = roomId,
        senderId = senderId,
        content = content,
        messageType = messageType,
    ).withId(id)
}
