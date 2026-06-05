package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

interface QuizSetRepositoryCustom {
    fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet>

    /** 마감(endDate < now)됐고 아직 매칭 후보가 없는 퀴즈셋 — 매칭 배치 대상 (match_candidate anti-join) */
    fun findEndedQuizSetsWithoutCandidates(now: LocalDateTime): List<QuizSet>
}
