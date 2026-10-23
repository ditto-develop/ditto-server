package com.ditto.domain.chat.repository.querydsl

data class ChatRoomSenderCount(
    val roomId: Long,
    val count: Long,
)
