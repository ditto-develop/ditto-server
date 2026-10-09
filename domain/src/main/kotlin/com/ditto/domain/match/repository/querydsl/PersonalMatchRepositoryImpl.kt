package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.entity.QPersonalMatch.personalMatch
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Transactional(readOnly = true)
class PersonalMatchRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : PersonalMatchRepositoryCustom {

    override fun existsMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberId: Long,
    ): Boolean = queryFactory
        .selectOne()
        .from(personalMatch)
        .where(
            personalMatch.quizSetId.eq(quizSetId),
            personalMatch.status.eq(status),
            personalMatch.memberId1.eq(memberId).or(personalMatch.memberId2.eq(memberId)),
        )
        .fetchFirst() != null

    override fun findMatchByQuizSetIdAndStatusAndMemberId(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberId: Long,
    ): PersonalMatch? = queryFactory
        .selectFrom(personalMatch)
        .where(
            personalMatch.quizSetId.eq(quizSetId),
            personalMatch.status.eq(status),
            personalMatch.memberId1.eq(memberId).or(personalMatch.memberId2.eq(memberId)),
        )
        .fetchFirst()

    override fun findPairMemberIdsById(id: Long): Pair<Long, Long>? {
        val row = queryFactory
            .select(personalMatch.memberId1, personalMatch.memberId2)
            .from(personalMatch)
            .where(personalMatch.id.eq(id))
            .fetchOne()
            ?: return null
        val memberId1 = row.get(personalMatch.memberId1) ?: error("member_id_1 이 비어 있습니다: id=$id")
        val memberId2 = row.get(personalMatch.memberId2) ?: error("member_id_2 가 비어 있습니다: id=$id")
        return memberId1 to memberId2
    }

    override fun findAllByQuizSetIdAndStatusAndAnyMemberIdIn(
        quizSetId: Long,
        status: PersonalMatchStatus,
        memberIds: Collection<Long>,
    ): List<PersonalMatch> = queryFactory
        .selectFrom(personalMatch)
        .where(
            personalMatch.quizSetId.eq(quizSetId),
            personalMatch.status.eq(status),
            personalMatch.memberId1.`in`(memberIds).or(personalMatch.memberId2.`in`(memberIds)),
        )
        .fetch()

    override fun existsPendingOfMemberInWeek(memberId: Long, weekStartedOn: LocalDate): Boolean =
        queryFactory
            .selectOne()
            .from(personalMatch)
            .join(quizSet).on(personalMatch.quizSetId.eq(quizSet.id))
            .where(
                personalMatch.memberId1.eq(memberId).or(personalMatch.memberId2.eq(memberId)),
                personalMatch.status.eq(PersonalMatchStatus.PENDING),
                quizSet.weekStartedOn.eq(weekStartedOn),
            )
            .fetchFirst() != null
}
