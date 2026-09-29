package com.ditto.domain.refreshtoken.repository.querydsl

import java.time.LocalDateTime

interface RefreshTokenRepositoryCustom {
    fun deleteAllByMemberId(memberId: Long)

    fun deleteExpiredByMemberId(memberId: Long, now: LocalDateTime)
}
