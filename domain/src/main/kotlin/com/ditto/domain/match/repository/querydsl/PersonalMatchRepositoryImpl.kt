package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.entity.QPersonalMatch.personalMatch
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

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
}
