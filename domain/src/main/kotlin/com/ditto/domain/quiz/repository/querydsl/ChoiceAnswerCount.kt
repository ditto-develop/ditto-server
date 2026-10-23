package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.entity.Gender
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
    /** 성별 미입력 답과 0건 행(답이 없거나 조건에 안 맞음)이 null 로 묶인다. */
    val gender: Gender?,
    val count: Long,
)
