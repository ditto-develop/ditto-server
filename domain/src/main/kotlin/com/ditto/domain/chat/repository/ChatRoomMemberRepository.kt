package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoomMember
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomMemberRepository : JpaRepository<ChatRoomMember, Long> {

    /** 내가 참여한 채팅방 멤버 레코드 목록 */
    fun findByMemberId(memberId: Long): List<ChatRoomMember>

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatRoomMember>

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean
}
