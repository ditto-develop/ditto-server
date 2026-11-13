package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.member.entity.Gender

data class PersonalMatchStatusCount(
    val gender: Gender?,
    val status: PersonalMatchStatus,
    val count: Long,
)
