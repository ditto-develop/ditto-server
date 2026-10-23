package com.ditto.api.admin.stats.dto

import com.ditto.domain.quiz.repository.querydsl.CompletedAgainCount
import com.ditto.domain.quiz.repository.querydsl.CompletionWithJoinedAt
import com.ditto.domain.quiz.repository.querydsl.MatchedCompletionCount
import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import java.time.LocalDate

data class RetentionStatsData(
    val previousRoundWeek: LocalDate?,
    val previousProgressCounts: List<QuizProgressCount>,
    val completedAgainCounts: List<CompletedAgainCount>,
    val matchedPreviousCompletionCounts: List<MatchedCompletionCount>,
    val matchedCompletedAgainCounts: List<CompletedAgainCount>,
    val cohortRoundWeeks: List<LocalDate>,
    val completionsWithJoinedAt: List<CompletionWithJoinedAt>,
) {
    companion object {
        val NO_PREVIOUS_ROUND = RetentionStatsData(
            previousRoundWeek = null,
            previousProgressCounts = emptyList(),
            completedAgainCounts = emptyList(),
            matchedPreviousCompletionCounts = emptyList(),
            matchedCompletedAgainCounts = emptyList(),
            cohortRoundWeeks = emptyList(),
            completionsWithJoinedAt = emptyList(),
        )
    }
}
