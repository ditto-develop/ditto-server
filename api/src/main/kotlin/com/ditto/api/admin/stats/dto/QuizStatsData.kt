package com.ditto.api.admin.stats.dto

import com.ditto.domain.quiz.repository.querydsl.ChoiceAnswerCount
import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import java.time.LocalDate

data class QuizStatsData(
    val progressCounts: List<QuizProgressCount>,
    val activeRoundCount: Int,
    val activeRoundWeeks: List<LocalDate>,
    val activeMemberCount: Long,
    val activeMemberWithPushDeviceCount: Long,
    val choiceAnswerCounts: List<ChoiceAnswerCount>,
)
