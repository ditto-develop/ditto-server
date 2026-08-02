package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import java.time.LocalDateTime

data class ChatRoomResponse(
    val roomId: Long,
    val roomType: ChatRoomType,
    // 나를 제외한 방 참여자들. 1:1이면 1명, 그룹이면 여러 명.
    val counterpartMemberIds: List<Long>,
    val lastMessage: ChatMessageResponse?,
    val unreadCount: Long,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun of(
            room: ChatRoom,
            counterpartMemberIds: List<Long>,
            lastMessage: ChatMessageResponse?,
            unreadCount: Long,
        ): ChatRoomResponse = ChatRoomResponse(
            roomId = room.id,
            roomType = room.roomType,
            counterpartMemberIds = counterpartMemberIds,
            lastMessage = lastMessage,
            unreadCount = unreadCount,
            createdAt = room.createdAt,
        )
    }
}
