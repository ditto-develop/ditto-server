package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoomMember
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomMemberRepository : JpaRepository<ChatRoomMember, Long> {

    /** 내가 참여한 채팅방 멤버 레코드 목록 */
    fun findByMemberId(memberId: Long): List<ChatRoomMember>

    /** 내가 참여한 채팅방 수 — 프로필 통계의 "매칭 성사"(= 채팅방 개설 횟수)용 */
    fun countByMemberId(memberId: Long): Long

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatRoomMember>

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean
}
