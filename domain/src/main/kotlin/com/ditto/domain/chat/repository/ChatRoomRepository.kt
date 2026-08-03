package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.querydsl.ChatRoomRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomRepository : JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {

    fun findByRoomTypeAndSourceId(roomType: ChatRoomType, sourceId: Long): ChatRoom?

    fun existsByRoomTypeAndSourceId(roomType: ChatRoomType, sourceId: Long): Boolean
}
