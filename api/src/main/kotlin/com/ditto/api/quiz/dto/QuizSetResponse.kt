package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

data class QuizSetResponse(
    val id: Long,
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
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(quizSet: QuizSet) = QuizSetResponse(
            id = quizSet.id,
            year = quizSet.year,
            month = quizSet.month,
            week = quizSet.week,
            category = quizSet.category,
            title = quizSet.title,
            description = quizSet.description,
            startDate = quizSet.startDate,
            endDate = quizSet.endDate,
            isActive = quizSet.isActive,
            matchingType = quizSet.matchingType,
            createdAt = quizSet.createdAt,
            updatedAt = quizSet.updatedAt,
        )
    }
}
