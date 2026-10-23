package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.member.entity.Gender

data class GroupMatchMemberCount(
    val isGroupActive: Boolean,
    val status: InvitationStatus,
    val gender: Gender?,
    val count: Long,
)
