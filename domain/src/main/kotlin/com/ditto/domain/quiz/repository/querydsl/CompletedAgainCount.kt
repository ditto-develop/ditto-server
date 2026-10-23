package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.MatchingType

data class CompletedAgainCount(
    val previousMatchingType: MatchingType,
    /** 고른 회차에 다시 푼 유형. 트랙 전환을 본다. */
    val matchingType: MatchingType,
    val count: Long,
)
