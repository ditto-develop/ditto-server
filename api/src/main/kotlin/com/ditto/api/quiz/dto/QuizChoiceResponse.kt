package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.QuizChoice

data class QuizChoiceResponse(
    val id: Long,
    val content: String,
    val order: Int,
) {
    companion object {
        fun from(choice: QuizChoice) = QuizChoiceResponse(
            id = choice.id,
            content = choice.content,
            order = choice.displayOrder,
        )
    }
}
