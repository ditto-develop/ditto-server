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
    /** 답이 없거나 조건에 맞지 않는 답은 회원이 조인되지 않아 null 이고 count 는 0이다. 성별 미입력도 null 이다. */
    val gender: Gender?,
    val count: Long,
)
