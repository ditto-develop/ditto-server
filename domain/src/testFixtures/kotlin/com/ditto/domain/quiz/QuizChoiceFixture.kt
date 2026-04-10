package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.withId

object QuizChoiceFixture {

    fun create(
        quizId: Long = 1L,
        content: String = "선택 질문 내용",
        displayOrder: Int = 1,
        id: Long = 0L,
    ): QuizChoice = QuizChoice.create(
        quizId = quizId,
        content = content,
        displayOrder = displayOrder,
    ).withId(id)
}
