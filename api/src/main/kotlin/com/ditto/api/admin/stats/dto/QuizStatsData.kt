package com.ditto.api.admin.stats.dto

import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import java.time.LocalDate

data class QuizStatsData(
    val progressCounts: List<QuizProgressCount>,
    val activeRoundWeeks: List<LocalDate>,
    val activeMemberCount: Long,
)
