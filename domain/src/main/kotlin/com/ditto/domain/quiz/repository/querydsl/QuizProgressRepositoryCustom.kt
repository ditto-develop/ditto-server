package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate

interface QuizProgressRepositoryCustom {
    fun deleteByMemberIdAndQuizSetIds(memberId: Long, quizSetIds: List<Long>)

    /**
     * 두 회원이 **함께 완주(COMPLETED)** 한 가장 최근 퀴즈셋 id. 함께 완주한 셋이 없으면 null.
     *
     * 답변 일치 비교의 기준 퀴즈셋을 고르는 데 쓴다 — 둘 다 완주해야 문항 수가 같아 비교가 성립한다.
     * "최근"은 id 내림차순으로 판단한다(auto-increment라 생성 순서 = 시간 순서).
     */
    fun findLatestQuizSetIdCompletedByBoth(memberId: Long, otherMemberId: Long): Long?

    /** 그 주 활성 퀴즈셋의 진행을 타입·상태·성별로 묶은 수. matchingType 이 null 이면 두 타입 모두. */
    fun countProgressesInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<QuizProgressCount>

    /** 여러 주에 걸쳐 완주했어도 한 명으로 센다. */
    fun countMembersCompletedInWeeks(
        weekStartedOns: Collection<LocalDate>,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long
}
