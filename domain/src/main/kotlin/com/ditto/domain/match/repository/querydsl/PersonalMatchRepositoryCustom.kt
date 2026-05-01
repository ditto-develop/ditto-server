package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus

interface PersonalMatchRepositoryCustom {

    fun existsMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberId: Long,
    ): Boolean

    /** 특정 퀴즈셋에서 memberId가 포함된 특정 상태의 매칭 조회 (방향 무관) */
    fun findMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberId: Long,
    ): PersonalMatch?
}
