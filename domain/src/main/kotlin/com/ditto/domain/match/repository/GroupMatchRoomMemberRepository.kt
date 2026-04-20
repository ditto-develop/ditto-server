package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchRoomMember
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchRoomMemberRepository : JpaRepository<GroupMatchRoomMember, Long> {

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByRoomId(roomId: Long): List<GroupMatchRoomMember>
}
