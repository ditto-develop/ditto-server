package com.ditto.api.quiz.dto

data class CurrentWeekQuizSetsResponse(
    val year: Int,
    val month: Int,
    val week: Int,
    val quizSets: List<CurrentWeekQuizSetResponse>,
) {
    companion object {
        fun empty(year: Int, month: Int, week: Int) = CurrentWeekQuizSetsResponse(
            year = year,
            month = month,
            week = week,
            quizSets = emptyList(),
        )
    }
}
