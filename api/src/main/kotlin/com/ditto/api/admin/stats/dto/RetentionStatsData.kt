package com.ditto.api.admin.stats.dto

import com.ditto.domain.quiz.repository.querydsl.CompletedAgainCount
import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import java.time.LocalDate

data class RetentionStatsData(
    val previousRoundWeek: LocalDate?,
    val previousProgressCounts: List<QuizProgressCount>,
    val completedAgainCounts: List<CompletedAgainCount>,
) {
    companion object {
        val NO_PREVIOUS_ROUND = RetentionStatsData(null, emptyList(), emptyList())
    }
}
