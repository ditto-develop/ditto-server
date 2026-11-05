package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatQuizCard
import org.springframework.data.jpa.repository.JpaRepository

interface ChatQuizCardRepository : JpaRepository<ChatQuizCard, Long> {
    fun findByRoomId(roomId: Long): List<ChatQuizCard>

    fun existsByRoomIdAndQuizId(roomId: Long, quizId: Long): Boolean

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatQuizCard>
}
