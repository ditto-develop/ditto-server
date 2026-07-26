package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.system.OperationWeek
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
            val week = OperationWeek(quizSet.weekStartedOn)
            return CurrentWeekQuizSetResponse(
                id = quizSet.id,
                weekStartedOn = week.startedOn,
                year = week.year,
                month = week.month,
                week = week.weekOfMonth,
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
