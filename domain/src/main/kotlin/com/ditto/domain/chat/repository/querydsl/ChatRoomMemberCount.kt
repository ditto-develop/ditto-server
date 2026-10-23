package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType

data class ChatRoomMemberCount(
    val roomId: Long,
    val roomType: ChatRoomType,
    val count: Long,
)
