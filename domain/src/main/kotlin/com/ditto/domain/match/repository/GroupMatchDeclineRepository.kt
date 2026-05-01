package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchDecline
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchDeclineRepository : JpaRepository<GroupMatchDecline, Long> {

    fun existsByQuizSetIdAndMemberId(quizSetId: Long, memberId: Long): Boolean
}
