package com.ditto.domain.memberreport.repository.querydsl

import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.matchesFilter
import com.ditto.domain.memberreport.entity.QMemberReport.memberReport
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
import java.time.LocalDateTime

@Transactional(readOnly = true)
class MemberReportRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MemberReportRepositoryCustom {

    override fun countMemberReportsReceivedInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<MemberReportCount> {
        val receivedRange = OperationWeek(weekStartedOn).dateTimeRange
        val countExpression = memberReport.count()
        return queryFactory
            .select(memberReport.reasons, memberReport.status, countExpression)
            .from(memberReport)
            .join(member).on(member.id.eq(memberReport.reportedMemberId))
            .where(
                memberReport.createdAt.goe(receivedRange.start),
                memberReport.createdAt.lt(receivedRange.endExclusive),
                member.matchesFilter(filter),
                matchingType?.let { reportedMemberCompletedQuizSetInWeek(weekStartedOn, it) },
            )
            .groupBy(memberReport.reasons, memberReport.status)
            .fetch()
            .map { row ->
                MemberReportCount(
                    reasons = row.get(memberReport.reasons).orEmpty(),
                    status = row.get(memberReport.status) ?: throw IllegalStateException("group by 키 status 가 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun findMostReportedMembers(
        receivedRange: OpenEndRange<LocalDateTime>,
        filter: MemberFilter,
        limit: Long,
    ): List<ReportedMemberCount> {
        val countExpression = memberReport.count()
        return queryFactory
            .select(member.id, member.nickname, countExpression)
            .from(memberReport)
            .join(member).on(member.id.eq(memberReport.reportedMemberId))
            .where(
                memberReport.createdAt.goe(receivedRange.start),
                memberReport.createdAt.lt(receivedRange.endExclusive),
                member.matchesFilter(filter),
            )
            .groupBy(member.id, member.nickname)
            .orderBy(countExpression.desc(), member.id.asc())
            .limit(limit)
            .fetch()
            .map { row ->
                ReportedMemberCount(
                    memberId = row.get(member.id) ?: throw IllegalStateException("group by 키 member.id 가 비었다"),
                    nickname = row.get(member.nickname)
                        ?: throw IllegalStateException("group by 키 member.nickname 이 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    private fun reportedMemberCompletedQuizSetInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType,
    ): BooleanExpression =
        JPAExpressions.selectOne()
            .from(quizProgress)
            .join(quizSet).on(quizSet.id.eq(quizProgress.quizSetId), quizSet.isActive.isTrue)
            .where(
                quizProgress.memberId.eq(memberReport.reportedMemberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                quizSet.weekStartedOn.eq(weekStartedOn),
                quizSet.matchingType.eq(matchingType),
            )
            .exists()
}
