package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
import com.querydsl.core.types.dsl.Expressions
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

    override fun existsPairByQuizSetId(oneMemberId: Long, otherMemberId: Long, quizSetId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(matchCandidate)
            .where(
                matchCandidate.quizSetId.eq(quizSetId),
                pairInEitherDirection(oneMemberId, otherMemberId),
            )
            .fetchFirst() != null

    /** (owner=one, other=other) 또는 그 반대 — 방향 무관 페어 조건 */
    private fun pairInEitherDirection(oneMemberId: Long, otherMemberId: Long) =
        Expressions.anyOf(
            matchCandidate.ownerMemberId.eq(oneMemberId).and(matchCandidate.otherMemberId.eq(otherMemberId)),
            matchCandidate.ownerMemberId.eq(otherMemberId).and(matchCandidate.otherMemberId.eq(oneMemberId)),
        )
}
