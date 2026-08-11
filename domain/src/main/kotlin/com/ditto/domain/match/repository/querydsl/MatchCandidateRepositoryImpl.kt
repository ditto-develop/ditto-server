package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class MatchCandidateRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MatchCandidateRepositoryCustom {

    override fun findOwnerMemberIdsByQuizSetId(quizSetId: Long): List<Long> =
        queryFactory
            .select(matchCandidate.ownerMemberId)
            .distinct()
            .from(matchCandidate)
            .where(matchCandidate.quizSetId.eq(quizSetId))
            .fetch()
}
