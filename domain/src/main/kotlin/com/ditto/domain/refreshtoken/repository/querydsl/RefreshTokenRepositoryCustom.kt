package com.ditto.domain.refreshtoken.repository.querydsl

interface RefreshTokenRepositoryCustom {
    fun deleteAllByMemberId(memberId: Long)
}
