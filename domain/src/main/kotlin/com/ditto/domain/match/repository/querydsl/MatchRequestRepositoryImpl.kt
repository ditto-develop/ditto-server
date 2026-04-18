package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.MatchRequestStatus
import com.ditto.domain.match.entity.QMatchRequest.matchRequest
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class MatchRequestRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MatchRequestRepositoryCustom {

    override fun existsMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: MatchRequestStatus,
        memberId: Long,
    ): Boolean = queryFactory
        .selectOne()
        .from(matchRequest)
        .where(
            matchRequest.quizSetId.eq(quizSetId),
            matchRequest.status.eq(status),
            matchRequest.fromMemberId.eq(memberId).or(matchRequest.toMemberId.eq(memberId)),
        )
        .fetchFirst() != null
}
