package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType

data class ChoiceAnswerCount(
    val quizSetId: Long,
    val matchingType: MatchingType,
    val quizId: Long,
    val quizDisplayOrder: Int,
    val question: String,
    val choiceId: Long,
    val choiceDisplayOrder: Int,
    val choiceContent: String,
    val answerCount: Long,
)
