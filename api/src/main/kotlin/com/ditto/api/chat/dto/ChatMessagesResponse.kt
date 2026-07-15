package com.ditto.api.chat.dto

data class ChatMessagesResponse(
    val messages: List<ChatMessageResponse>,
    val nextCursor: Long?,
)
