package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatch
import org.springframework.data.jpa.repository.JpaRepository

interface GroupMatchRepository : JpaRepository<GroupMatch, Long> {

    /** 아직 활성화되지 않은 방 중 가장 먼저 생성된 방 반환 (join 시 배정용) */
    fun findFirstByQuizSetIdAndIsActiveFalseOrderByCreatedAtAsc(quizSetId: Long): GroupMatch?

    /** 해당 퀴즈셋의 그룹 전체 — 후보 재생성 시 기존 그룹을 판별하는 데 쓴다. */
    fun findByQuizSetId(quizSetId: Long): List<GroupMatch>
}
