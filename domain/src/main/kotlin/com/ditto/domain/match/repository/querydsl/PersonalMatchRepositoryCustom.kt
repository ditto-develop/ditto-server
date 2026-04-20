package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatchStatus

interface PersonalMatchRepositoryCustom {

    fun existsMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberId: Long,
    ): Boolean
}
