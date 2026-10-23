package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType

data class CompletedAgainCount(
    val previousMatchingType: MatchingType,
    val againMatchingType: MatchingType,
    val count: Long,
)
