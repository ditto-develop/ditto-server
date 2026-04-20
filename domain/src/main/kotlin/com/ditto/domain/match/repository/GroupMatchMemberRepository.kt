package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchMember
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchMemberRepository : JpaRepository<GroupMatchMember, Long> {

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByRoomId(roomId: Long): List<GroupMatchMember>
}
