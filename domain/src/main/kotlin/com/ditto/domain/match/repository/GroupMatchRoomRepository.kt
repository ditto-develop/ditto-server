package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchRoom
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchRoomRepository : JpaRepository<GroupMatchRoom, Long> {

    /** 아직 활성화되지 않은 방 중 가장 먼저 생성된 방 반환 (join 시 배정용) */
    fun findFirstByQuizSetIdAndIsActiveFalseOrderByCreatedAtAsc(quizSetId: Long): GroupMatchRoom?
}
