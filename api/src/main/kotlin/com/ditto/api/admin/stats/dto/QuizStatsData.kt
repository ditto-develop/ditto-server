package com.ditto.api.admin.stats.dto

import com.ditto.domain.quiz.repository.querydsl.ChoiceAnswerCount
import com.ditto.domain.quiz.repository.querydsl.QuizProgressCount
import java.time.LocalDate
import java.time.LocalDateTime

data class QuizStatsData(
    val progressCounts: List<QuizProgressCount>,
    val activeRoundCount: Int,
    val activeRoundWeeks: List<LocalDate>,
    val activeMemberCount: Long,
    val activeMemberWithPushDeviceCount: Long,
    val completedAts: List<LocalDateTime>,
    val choiceAnswerCounts: List<ChoiceAnswerCount>,
)
