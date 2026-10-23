package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.member.entity.Gender

data class ActiveGroupMemberCount(
    val groupMatchId: Long,
    val gender: Gender?,
    val count: Long,
)
