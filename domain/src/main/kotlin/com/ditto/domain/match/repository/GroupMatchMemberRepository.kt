package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchMemberRepository : JpaRepository<GroupMatchMember, Long>, GroupMatchMemberRepositoryCustom {

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByRoomId(roomId: Long): List<GroupMatchMember>
}
