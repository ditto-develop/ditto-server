package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus

data class QuizProgressCount(
    val matchingType: MatchingType,
    val status: QuizProgressStatus,
    val gender: Gender?,
    val count: Long,
)
