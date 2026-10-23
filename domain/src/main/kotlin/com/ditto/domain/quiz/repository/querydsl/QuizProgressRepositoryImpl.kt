package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.nonAdminMatching
import com.ditto.domain.notification.entity.QMemberDevice.memberDevice
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QQuizProgress
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.quiz.entity.QQuizSet
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Transactional
class QuizProgressRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : QuizProgressRepositoryCustom {

    override fun deleteByMemberIdAndQuizSetIds(memberId: Long, quizSetIds: List<Long>) {
        if (quizSetIds.isEmpty()) return
        queryFactory
            .delete(quizProgress)
            .where(
                quizProgress.memberId.eq(memberId),
                quizProgress.quizSetId.`in`(quizSetIds),
            )
            .execute()
    }

    override fun findLatestQuizSetIdCompletedByBoth(memberId: Long, otherMemberId: Long): Long? {
        val otherProgress = QQuizProgress("otherProgress")

        return queryFactory
            .select(quizProgress.quizSetId)
            .from(quizProgress)
            .join(otherProgress)
            .on(
                otherProgress.quizSetId.eq(quizProgress.quizSetId),
                otherProgress.memberId.eq(otherMemberId),
                otherProgress.status.eq(QuizProgressStatus.COMPLETED),
            )
            .where(
                quizProgress.memberId.eq(memberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
            )
            .orderBy(quizProgress.quizSetId.desc())
            .fetchFirst()
    }

    override fun countProgressesInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<QuizProgressCount> {
        val countExpression = quizProgress.count()
        return queryFactory
            .select(quizSet.matchingType, quizProgress.status, member.gender, countExpression)
            .fromProgressOfActiveSetsWithMember()
            .where(
                quizSet.weekStartedOn.eq(weekStartedOn),
                matchingTypeIs(matchingType),
                member.nonAdminMatching(filter),
            )
            .groupBy(quizSet.matchingType, quizProgress.status, member.gender)
            .fetch()
            .map { row ->
                QuizProgressCount(
                    matchingType = row.get(quizSet.matchingType)
                        ?: throw IllegalStateException("group by 키 matchingType 이 비었다"),
                    status = row.get(quizProgress.status) ?: throw IllegalStateException("group by 키 status 가 비었다"),
                    gender = row.get(member.gender),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    override fun countMembersCompletedInWeeks(
        roundWeeks: Collection<LocalDate>,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long {
        if (roundWeeks.isEmpty()) return 0L
        return queryFactory
            .select(quizProgress.memberId.countDistinct())
            .fromProgressOfActiveSetsWithMember()
            .where(
                quizSet.weekStartedOn.`in`(roundWeeks),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                matchingTypeIs(matchingType),
                member.nonAdminMatching(filter),
            )
            .fetchOne() ?: 0L
    }

    override fun countMembersCompletedInWeeksWithPushDevice(
        roundWeeks: Collection<LocalDate>,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): Long {
        if (roundWeeks.isEmpty()) return 0L
        return queryFactory
            .select(quizProgress.memberId.countDistinct())
            .fromProgressOfActiveSetsWithMember()
            .where(
                quizSet.weekStartedOn.`in`(roundWeeks),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                matchingTypeIs(matchingType),
                member.nonAdminMatching(filter),
                queryFactory.selectOne()
                    .from(memberDevice)
                    .where(memberDevice.memberId.eq(quizProgress.memberId))
                    .exists(),
            )
            .fetchOne() ?: 0L
    }

    override fun countActiveStatusMembersCompletedInWeeks(roundWeeks: Collection<LocalDate>, filter: MemberFilter): Long {
        if (roundWeeks.isEmpty()) return 0L
        return queryFactory
            .select(quizProgress.memberId.countDistinct())
            .fromProgressOfActiveSetsWithMember()
            .where(
                quizSet.weekStartedOn.`in`(roundWeeks),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                member.status.eq(MemberStatus.ACTIVE),
                member.nonAdminMatching(filter),
            )
            .fetchOne() ?: 0L
    }

    override fun countMembersCompletedAgain(
        previousWeekStartedOn: LocalDate,
        weekStartedOn: LocalDate,
        filter: MemberFilter,
    ): List<CompletedAgainCount> {
        val againProgress = QQuizProgress("againProgress")
        val againSet = QQuizSet("againSet")
        val countExpression = quizProgress.memberId.countDistinct()
        return queryFactory
            .select(quizSet.matchingType, againSet.matchingType, countExpression)
            .fromProgressOfActiveSetsWithMember()
            .join(againProgress).on(
                againProgress.memberId.eq(quizProgress.memberId),
                againProgress.status.eq(QuizProgressStatus.COMPLETED),
            )
            .join(againSet).on(
                againSet.id.eq(againProgress.quizSetId),
                againSet.isActive.isTrue,
                againSet.weekStartedOn.eq(weekStartedOn),
            )
            .where(
                quizSet.weekStartedOn.eq(previousWeekStartedOn),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                member.nonAdminMatching(filter),
            )
            .groupBy(quizSet.matchingType, againSet.matchingType)
            .fetch()
            .map { row ->
                CompletedAgainCount(
                    previousMatchingType = row.get(quizSet.matchingType)
                        ?: throw IllegalStateException("group by 키 matchingType 이 비었다"),
                    againMatchingType = row.get(againSet.matchingType)
                        ?: throw IllegalStateException("group by 키 againSet.matchingType 이 비었다"),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }

    // 같은 주에 QA 용 비활성 셋이 있을 수 있어 매칭과 같이 활성 셋만 본다.
    private fun <T> JPAQuery<T>.fromProgressOfActiveSetsWithMember(): JPAQuery<T> =
        from(quizProgress)
            .join(quizSet).on(quizSet.id.eq(quizProgress.quizSetId), quizSet.isActive.isTrue)
            .join(member).on(member.id.eq(quizProgress.memberId))

    private fun matchingTypeIs(matchingType: MatchingType?): BooleanExpression? =
        matchingType?.let { quizSet.matchingType.eq(it) }
}
