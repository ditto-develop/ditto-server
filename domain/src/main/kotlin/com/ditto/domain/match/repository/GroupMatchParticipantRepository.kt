package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchParticipant
import com.ditto.domain.match.entity.GroupMatchParticipantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchParticipantRepository : JpaRepository<GroupMatchParticipant, Long> {

    fun findByQuizSetIdAndMemberId(quizSetId: Long, memberId: Long): GroupMatchParticipant?

    fun existsByQuizSetIdAndMemberIdAndStatus(
        quizSetId: Long,
        memberId: Long,
        status: GroupMatchParticipantStatus,
    ): Boolean
}
