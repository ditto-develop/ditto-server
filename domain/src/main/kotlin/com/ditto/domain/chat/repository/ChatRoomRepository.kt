package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {

    fun findByRoomTypeAndSourceId(roomType: ChatRoomType, sourceId: Long): ChatRoom?

    fun existsByRoomTypeAndSourceId(roomType: ChatRoomType, sourceId: Long): Boolean
}
