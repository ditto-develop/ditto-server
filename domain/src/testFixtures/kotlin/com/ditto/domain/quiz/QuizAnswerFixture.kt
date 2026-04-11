package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.withId

object QuizAnswerFixture {

    fun create(
        memberId: Long = 1L,
        quizId: Long = 1L,
        choiceId: Long = 1L,
        id: Long = 0L,
    ): QuizAnswer = QuizAnswer.create(
        memberId = memberId,
        quizId = quizId,
        choiceId = choiceId,
    ).withId(id)
}
