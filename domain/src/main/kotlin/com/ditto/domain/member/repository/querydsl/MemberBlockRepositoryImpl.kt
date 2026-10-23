package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.entity.QMemberBlock.memberBlock
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.system.OperationWeek
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Transactional(readOnly = true)
class MemberBlockRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MemberBlockRepositoryCustom {

    override fun existsBetween(oneId: Long, otherId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(memberBlock)
            .where(
                memberBlock.blockerId.eq(oneId).and(memberBlock.blockedMemberId.eq(otherId))
                    .or(memberBlock.blockerId.eq(otherId).and(memberBlock.blockedMemberId.eq(oneId))),
            )
            .fetchFirst() != null

    override fun findAllInvolving(memberIds: Collection<Long>): List<MemberBlock> {
        if (memberIds.isEmpty()) return emptyList()
        return queryFactory
            .selectFrom(memberBlock)
            .where(
                memberBlock.blockerId.`in`(memberIds)
                    .or(memberBlock.blockedMemberId.`in`(memberIds)),
            )
            .fetch()
    }

    override fun countBlocksCreatedInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long {
        val createdRange = OperationWeek(weekStartedOn).dateTimeRange
        return queryFactory
            .select(memberBlock.count())
            .from(memberBlock)
            .join(member).on(member.id.eq(memberBlock.blockedMemberId))
            .where(
                memberBlock.createdAt.goe(createdRange.start),
                memberBlock.createdAt.lt(createdRange.endExclusive),
                member.nonAdminMatching(filter),
                matchingType?.let { blockedMemberCompletedQuizSetInWeek(weekStartedOn, it) },
            )
            .fetchOne() ?: 0L
    }

    private fun blockedMemberCompletedQuizSetInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType,
    ): BooleanExpression =
        JPAExpressions.selectOne()
            .from(quizProgress)
            .join(quizSet).on(quizSet.id.eq(quizProgress.quizSetId), quizSet.isActive.isTrue)
            .where(
                quizProgress.memberId.eq(memberBlock.blockedMemberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                quizSet.weekStartedOn.eq(weekStartedOn),
                quizSet.matchingType.eq(matchingType),
            )
            .exists()
}
