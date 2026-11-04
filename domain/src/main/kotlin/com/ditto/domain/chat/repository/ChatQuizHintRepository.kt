package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatQuizHint
import org.springframework.data.jpa.repository.JpaRepository

interface ChatQuizHintRepository : JpaRepository<ChatQuizHint, Long> {
    fun existsByRoomId(roomId: Long): Boolean

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatQuizHint>
}
