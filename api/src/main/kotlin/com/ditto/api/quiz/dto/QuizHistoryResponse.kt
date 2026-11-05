package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate
import java.time.LocalDateTime

/** 기록 탭(QH-01). 완주한 주만 담는다. */
data class QuizHistoryResponse(
    val summary: QuizHistorySummary,
    /** 이번 운영 주 퀴즈를 완주했을 때만. 아니면 null — 참여하기 노출은 `GET /quiz-sets/current-week`로 판단한다. */
    val currentWeek: QuizHistoryCurrentWeek?,
    /** 완주한 주, 최신순. 이번 주 포함. */
    val weeks: List<QuizHistoryWeek>,
)

data class QuizHistorySummary(
    val participatedWeeks: Int,
    val answeredQuestions: Int,
    /** 결과가 공개된(마감된) 주만 합산한다. */
    val minorityCount: Int,
)

data class QuizHistoryCurrentWeek(
    val quizSetId: Long,
    /** 결과 공개 시각이 지났는지 — 서버 시각 기준. 탭 뱃지('결과 공개')의 근거. */
    val resultOpen: Boolean,
    val resultOpensAt: LocalDateTime,
)

data class QuizHistoryWeek(
    val quizSetId: Long,
    val weekStartedOn: LocalDate,
    val matchingType: MatchingType,
    val title: String,
    /** 결과 공개 전이면 null. */
    val minorityCount: Int?,
)
