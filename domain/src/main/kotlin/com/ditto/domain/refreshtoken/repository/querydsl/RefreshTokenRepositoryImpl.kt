package com.ditto.domain.refreshtoken.repository.querydsl

import com.ditto.domain.refreshtoken.entity.QRefreshToken.refreshToken
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
class RefreshTokenRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : RefreshTokenRepositoryCustom {

    override fun deleteAllByMemberId(memberId: Long) {
        queryFactory
            .delete(refreshToken)
            .where(refreshToken.memberId.eq(memberId))
            .execute()
    }

    override fun deleteExpiredByMemberId(memberId: Long, now: LocalDateTime) {
        queryFactory
            .delete(refreshToken)
            .where(
                refreshToken.memberId.eq(memberId),
                refreshToken.expiresAt.lt(now),
            )
            .execute()
    }
}
