package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.QuizProgressStatus

data class QuizProgressResponse(
    val status: QuizProgressStatus,
    val quizSetId: Long?,
    val quizSetTitle: String?,
    val totalQuizzes: Int?,
    val answeredQuizzes: Int?,
    val participantCount: Long,
) {
    companion object {
        fun notStarted(participantCount: Long) =
            QuizProgressResponse(
                status = QuizProgressStatus.NOT_STARTED,
                quizSetId = null,
                quizSetTitle = null,
                totalQuizzes = null,
                answeredQuizzes = null,
                participantCount = participantCount,
            )
    }
}
