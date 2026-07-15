package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.repository.querydsl.ChatMessageRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {

    /** 방의 가장 최근 메시지 (목록 미리보기용) */
    fun findFirstByRoomIdOrderByIdDesc(roomId: Long): ChatMessage?

    /** 특정 메시지 ID 초과(= 아직 안 읽은) 메시지 개수 */
    fun countByRoomIdAndIdGreaterThan(roomId: Long, id: Long): Long

    fun countByRoomId(roomId: Long): Long
}
