package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.Location

data class LocationCount(
    val location: Location?,
    val count: Long,
)
