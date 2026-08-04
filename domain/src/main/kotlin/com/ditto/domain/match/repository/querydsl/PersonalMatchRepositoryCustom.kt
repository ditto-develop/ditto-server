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

    /**
     * 해당 회원이 낀 매칭 중 주어진 상태가 하나라도 있는지 (퀴즈셋 무관, 방향 무관) — 탈퇴 가드에 쓴다.
     * 페어가 (memberId1, memberId2)로 정규화돼 있어 양쪽 컬럼을 모두 본다.
     */
    fun existsByMemberIdAndStatusIn(memberId: Long, statuses: Collection<PersonalMatchStatus>): Boolean
}
