package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

interface QuizSetRepositoryCustom {
    fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet>

    /**
     * 마감(endDate < now)됐고 아직 매칭 후보가 없는 퀴즈셋 — 매칭 배치 대상.
     * 후보를 담는 테이블이 매칭 타입마다 달라 `match_candidate`·`group_match` 양쪽을 anti-join 한다.
     */
    fun findEndedQuizSetsWithoutCandidates(now: LocalDateTime): List<QuizSet>

    fun findLatestCompletedQuizSet(memberId: Long, matchingType: MatchingType): QuizSet?
}
