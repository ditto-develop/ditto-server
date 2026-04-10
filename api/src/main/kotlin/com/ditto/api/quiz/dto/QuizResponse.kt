package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import java.time.LocalDateTime

data class QuizResponse(
    val id: Long,
    val question: String,
    val quizSetId: Long,
    val choices: List<QuizChoiceResponse>,
    val order: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(quiz: Quiz, choices: List<QuizChoice>) = QuizResponse(
            id = quiz.id,
            question = quiz.question,
            quizSetId = quiz.quizSetId,
            choices = choices.map { QuizChoiceResponse.from(it) },
            order = quiz.displayOrder,
            createdAt = quiz.createdAt,
            updatedAt = quiz.updatedAt,
        )
    }
}
