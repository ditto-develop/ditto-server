package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDate

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

    /** 매칭의 두 회원 ID (`memberId1`, `memberId2`). 엔티티를 영속성 컨텍스트에 올리지 않으려고 값만 읽는다. */
    fun findPairMemberIdsById(id: Long): Pair<Long, Long>?

    /** 특정 퀴즈셋에서 주어진 회원 중 한 명이라도 낀 특정 상태의 매칭 목록 (방향 무관) */
    fun findAllByQuizSetIdAndStatusAndAnyMemberIdIn(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberIds: Collection<Long>,
    ): List<PersonalMatch>

    // 수락·거절은 이번 주 퀴즈셋만 받아서 지난 주 대기 신청은 응답할 수 없다. 탈퇴 검사가 그 주 것만 본다.
    fun existsPendingOfMemberInWeek(memberId: Long, weekStartedOn: LocalDate): Boolean
}
