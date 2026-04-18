package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchRoom
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchRoomRepository : JpaRepository<GroupMatchRoom, Long> {

    fun findByQuizSetId(quizSetId: Long): GroupMatchRoom?
}
