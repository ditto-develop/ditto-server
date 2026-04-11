package com.ditto.api.quiz.dto

data class SubmitAnswerRequest(
    val quizId: Long,
    val choiceId: Long,
)
