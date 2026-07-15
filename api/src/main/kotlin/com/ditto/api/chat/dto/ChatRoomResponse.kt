package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import java.time.LocalDateTime

data class ChatRoomResponse(
    val roomId: Long,
    val roomType: ChatRoomType,
    val counterpartMemberId: Long?,
    val lastMessage: ChatMessageResponse?,
    val unreadCount: Long,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun of(
            room: ChatRoom,
            counterpartMemberId: Long?,
            lastMessage: ChatMessageResponse?,
            unreadCount: Long,
        ): ChatRoomResponse = ChatRoomResponse(
            roomId = room.id,
            roomType = room.roomType,
            counterpartMemberId = counterpartMemberId,
            lastMessage = lastMessage,
            unreadCount = unreadCount,
            createdAt = room.createdAt,
        )
    }
}
