package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
import com.ditto.domain.match.entity.QPersonalMatch.personalMatch
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.nonAdminMatching
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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

    override fun countCandidateOwnersInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<MatchCandidateCount> {
        val countExpression = matchCandidate.ownerMemberId.countDistinct()
        return queryFactory
            .select(member.gender, countExpression)
            .from(matchCandidate)
            .join(quizSet).on(quizSet.id.eq(matchCandidate.quizSetId), quizSet.isActive.isTrue)
            .join(member).on(member.id.eq(matchCandidate.ownerMemberId))
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                member.nonAdminMatching(filter),
            )
            .groupBy(member.gender)
            .fetch()
            .map { row -> MatchCandidateCount(gender = row.get(member.gender), count = row.get(countExpression) ?: 0L) }
    }

    override fun findPairScoresInWeek(weekStartedOn: LocalDate): List<Double> =
        queryFactory
            .select(matchCandidate.score)
            .from(matchCandidate)
            .join(quizSet).on(quizSet.id.eq(matchCandidate.quizSetId), quizSet.isActive.isTrue)
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                matchCandidate.ownerMemberId.lt(matchCandidate.otherMemberId),
            )
            .fetch()
            .filterNotNull()

    override fun findAcceptedPairScoresInWeek(weekStartedOn: LocalDate): List<Double?> =
        queryFactory
            .select(matchCandidate.score)
            .from(personalMatch)
            .join(quizSet).on(quizSet.id.eq(personalMatch.quizSetId), quizSet.isActive.isTrue)
            .leftJoin(matchCandidate).on(
                matchCandidate.quizSetId.eq(personalMatch.quizSetId),
                matchCandidate.ownerMemberId.eq(personalMatch.memberId1),
                matchCandidate.otherMemberId.eq(personalMatch.memberId2),
            )
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                personalMatch.status.eq(PersonalMatchStatus.ACCEPTED),
            )
            .fetch()

    /** (owner=one, other=other) 또는 그 반대 — 방향 무관 페어 조건 */
    private fun pairInEitherDirection(oneMemberId: Long, otherMemberId: Long) =
        Expressions.anyOf(
            matchCandidate.ownerMemberId.eq(oneMemberId).and(matchCandidate.otherMemberId.eq(otherMemberId)),
            matchCandidate.ownerMemberId.eq(otherMemberId).and(matchCandidate.otherMemberId.eq(oneMemberId)),
        )
}
