package com.ditto.domain.match

import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.withId

object GroupMatchFixture {

    /**
     * 후보 그룹. [acceptedCount]만큼 수락을 기록해 성사 전/후 상태를 만든다
     * — 임계값에 닿으면 엔티티가 스스로 활성화하므로 isActive 를 따로 넣지 않는다.
     */
    fun create(
        quizSetId: Long = 1L,
        score: Double = 80.0,
        acceptedCount: Int = 0,
        id: Long = 0L,
    ): GroupMatch = GroupMatch.candidate(quizSetId = quizSetId, score = score)
        .also { room -> repeat(acceptedCount) { room.recordAcceptance() } }
        .withId(id)
}
