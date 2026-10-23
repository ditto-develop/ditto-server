package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.member.entity.Gender

data class MatchCandidateCount(
    val gender: Gender?,
    val count: Long,
)
