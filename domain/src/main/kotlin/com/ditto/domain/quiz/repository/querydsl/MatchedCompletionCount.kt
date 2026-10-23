package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType

data class MatchedCompletionCount(
    val matchingType: MatchingType,
    val count: Long,
)
