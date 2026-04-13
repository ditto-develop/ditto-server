package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.withId

object QuizProgressFixture {

    fun create(
        memberId: Long = 1L,
        quizSetId: Long = 1L,
        totalCount: Int = 5,
        id: Long = 0L,
    ): QuizProgress = QuizProgress.create(
        memberId = memberId,
        quizSetId = quizSetId,
        totalCount = totalCount,
    ).withId(id)
}
