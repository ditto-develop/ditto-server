package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.withId

object QuizFixture {

    fun create(
        quizSetId: Long = 1L,
        question: String = "테스트 퀴즈 질문입니다",
        displayOrder: Int = 1,
        id: Long = 0L,
    ): Quiz = Quiz.create(
        quizSetId = quizSetId,
        question = question,
        displayOrder = displayOrder,
    ).withId(id)
}
