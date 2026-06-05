package com.ditto.domain.match

import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.withId

object MatchCandidateFixture {

    fun create(
        ownerMemberId: Long = 1L,
        otherMemberId: Long = 2L,
        quizSetId: Long = 1L,
        score: Double = 80.0,
        matchedQuestionCount: Int = 8,
        totalQuestionCount: Int = 10,
        id: Long = 0L,
    ): MatchCandidate = MatchCandidate.create(
        ownerMemberId = ownerMemberId,
        otherMemberId = otherMemberId,
        quizSetId = quizSetId,
        score = score,
        matchedQuestionCount = matchedQuestionCount,
        totalQuestionCount = totalQuestionCount,
    ).withId(id)
}
