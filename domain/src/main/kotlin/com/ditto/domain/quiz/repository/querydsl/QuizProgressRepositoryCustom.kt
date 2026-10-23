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

    /** 그 주 활성 퀴즈셋의 진행을 유형·상태·성별로 묶은 수. matchingType 이 null 이면 두 유형 모두. */
    fun countProgressesInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<QuizProgressCount>

    /** 이전 주 완주자 중 이번 주에도 완주한 회원 수를 이전 주에 푼 유형과 이번 주에 푼 유형별로 센다. */
    fun countMembersCompletedAgain(
        previousWeekStartedOn: LocalDate,
        weekStartedOn: LocalDate,
        filter: MemberFilter,
    ): List<CompletedAgainCount>

    /** 여러 주에 걸쳐 완주했어도 한 명으로 센다. */
    fun countMembersCompletedInWeeks(
        roundWeeks: Collection<LocalDate>,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long

    /** countMembersCompletedInWeeks 와 같은 회원 중 지금 푸시 토큰이 하나라도 있는 회원 수. */
    fun countMembersCompletedInWeeksWithPushDevice(
        roundWeeks: Collection<LocalDate>,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long

    /** countMembersCompletedInWeeks 와 같은 회원 중 지금 ACTIVE 인 가입자 수. 매칭 유형은 따지지 않는다. */
    fun countActiveStatusMembersCompletedInWeeks(roundWeeks: Collection<LocalDate>, filter: MemberFilter): Long
}
