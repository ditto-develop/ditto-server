package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatMessageType

data class ChatSendRequest(
    val content: String,
    // TEXT: content=본문 / IMAGE: content=업로드한 S3 key
    val messageType: ChatMessageType = ChatMessageType.TEXT,
)
