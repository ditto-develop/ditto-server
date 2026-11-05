package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.repository.querydsl.ChatMessageRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatMessageRepository : JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {

    /** 방마다 마지막 메시지 시각. 방 목록을 방마다 조회하지 않고 한 번에 채운다. */
    @Query(
        "select m.roomId as roomId, max(m.createdAt) as lastMessageAt from ChatMessage m " +
            "where m.roomId in :roomIds group by m.roomId",
    )
    fun findLastMessageTimes(@Param("roomIds") roomIds: Collection<Long>): List<RoomLastMessageTime>

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatMessage>

    /** 방의 가장 최근 메시지 (목록 미리보기용) */
    fun findFirstByRoomIdOrderByIdDesc(roomId: Long): ChatMessage?

    /**
     * 특정 메시지 ID 초과(= 아직 안 읽은) 메시지 중 [excludedType]이 아닌 것의 개수.
     * 방 목록 안 읽음 수가 쓴다 — 서버가 만든 첫 대화 힌트는 읽을 거리로 세지 않는다.
     */
    fun countByRoomIdAndIdGreaterThanAndMessageTypeNot(roomId: Long, id: Long, excludedType: ChatMessageType): Long

    fun countByRoomId(roomId: Long): Long

    fun countByRoomIdAndMessageTypeNot(roomId: Long, excludedType: ChatMessageType): Long

    /** 읽음 커서로 보낸 id 가 이 방의 메시지인지. 다른 방 id 나 없는 id 로 커서가 전진하면 되돌릴 수 없다. */
    fun existsByIdAndRoomId(id: Long, roomId: Long): Boolean
}
