package com.ditto.api.quiz.dto

import com.ditto.domain.system.OperationWeek
import java.time.LocalDate

/** 주간 식별자는 weekStartedOn(운영 주 시작 월요일)이며 year/month/week는 그 파생 표시값. */
data class CurrentWeekQuizSetsResponse(
    val weekStartedOn: LocalDate,
    val year: Int,
    val month: Int,
    val week: Int,
    val quizSets: List<CurrentWeekQuizSetResponse>,
) {
    companion object {
        fun of(
            currentWeek: OperationWeek,
            quizSets: List<CurrentWeekQuizSetResponse>,
        ) = CurrentWeekQuizSetsResponse(
            weekStartedOn = currentWeek.startedOn,
            year = currentWeek.year,
            month = currentWeek.month,
            week = currentWeek.weekOfMonth,
            quizSets = quizSets,
        )

        fun empty(currentWeek: OperationWeek) = of(currentWeek, emptyList())
    }
}
