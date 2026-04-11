package com.ditto.domain.refreshtoken.repository.querydsl

import com.ditto.domain.refreshtoken.entity.QRefreshToken.refreshToken
import com.querydsl.jpa.impl.JPAQueryFactory

class RefreshTokenRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RefreshTokenRepositoryCustom {

    override fun deleteAllByMemberId(memberId: Long) {
        queryFactory
            .delete(refreshToken)
            .where(refreshToken.memberId.eq(memberId))
            .execute()
    }
}
