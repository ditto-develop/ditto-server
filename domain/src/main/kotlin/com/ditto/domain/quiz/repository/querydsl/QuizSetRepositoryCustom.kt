package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDate
import java.time.LocalDateTime

interface QuizSetRepositoryCustom {
    fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet>

    /**
     * [endedAfter] 이후 마감(endedAfter < endDate < now)됐고 아직 매칭 후보가 없는 퀴즈셋. 매칭 배치 대상이다.
     * 후보를 담는 테이블이 매칭 타입마다 달라 `match_candidate`·`group_match` 양쪽을 anti-join 한다.
     * 하한이 없으면 후보가 0건으로 끝난 셋이 매주 다시 잡힌다.
     */
    fun findEndedQuizSetsWithoutCandidates(endedAfter: LocalDateTime, now: LocalDateTime): List<QuizSet>

    /**
     * 회원이 **그 운영 주에** 완주(COMPLETED)한 해당 타입 퀴즈셋. 후보 조회·성사 전 열람 권한의 기준이다.
     *
     * 주차로 좁히지 않으면 "가장 최근에 완주한 셋"이 잡혀 지난 사이클 후보가 계속 노출된다 —
     * 후보 행(`match_candidate`·`group_match`)은 지난 주 것도 남기 때문이다.
     * 한 주에 같은 타입 퀴즈셋은 하나뿐이라 단건으로 돌려준다.
     */
    fun findCompletedQuizSetInWeek(memberId: Long, matchingType: MatchingType, weekStartedOn: LocalDate): QuizSet?
}
