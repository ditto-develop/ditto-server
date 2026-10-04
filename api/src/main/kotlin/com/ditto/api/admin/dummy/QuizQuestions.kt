package com.ditto.api.admin.dummy

import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet

/** 한 퀴즈셋의 문항과 문항별 선택지. 더미의 답을 고르는 데 쓴다. */
class QuizQuestions(
    val quizSet: QuizSet,
    val quizzes: List<Quiz>,
    private val choicesByQuizId: Map<Long, List<QuizChoice>>,
) {
    val quizSetId: Long get() = quizSet.id

    fun choicesOf(quiz: Quiz): List<QuizChoice> = choicesByQuizId.getValue(quiz.id)

    /** 앞에서 [answeredCount]개 문항의 답을 고른다. */
    fun pickChoices(answeredCount: Int, pick: (Quiz) -> QuizChoice): List<QuizChoice> =
        quizzes.take(answeredCount).map(pick)

    fun pickAllRandomly(): List<QuizChoice> = pickChoices(quizzes.size) { quiz -> choicesOf(quiz).random() }
}
