package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentWeekQuizSetResponse private constructor(
    val id: Long,
    val weekStartedOn: LocalDate,
    val year: Int,
    val month: Int,
    val week: Int,
    val category: String,
    val title: String,
    val description: String?,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val isActive: Boolean,
    val matchingType: MatchingType,
    val quizzes: List<QuizResponse>,
) {
    companion object {
        fun from(
            quizSet: QuizSet,
            quizzes: List<Quiz>,
            choicesByQuizId: Map<Long, List<QuizChoice>>,
        ): CurrentWeekQuizSetResponse {
            val operationWeek = quizSet.operationWeek
            return CurrentWeekQuizSetResponse(
                id = quizSet.id,
                weekStartedOn = operationWeek.startedOn,
                year = operationWeek.year,
                month = operationWeek.month,
                week = operationWeek.weekOfMonth,
                category = quizSet.category,
                title = quizSet.title,
                description = quizSet.description,
                startDate = quizSet.startDate,
                endDate = quizSet.endDate,
                isActive = quizSet.isActive,
                matchingType = quizSet.matchingType,
                quizzes =
                    quizzes.map { quiz ->
                        QuizResponse.from(quiz, choicesByQuizId[quiz.id] ?: emptyList())
                    },
            )
        }
    }
}
