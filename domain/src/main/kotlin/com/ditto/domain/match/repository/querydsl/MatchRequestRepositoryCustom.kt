package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.MatchRequestStatus

interface MatchRequestRepositoryCustom {

    fun existsMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: MatchRequestStatus,
        memberId: Long,
    ): Boolean
}
