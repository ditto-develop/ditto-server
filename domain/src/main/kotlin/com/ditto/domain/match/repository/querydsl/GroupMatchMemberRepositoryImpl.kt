package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QGroupMatchMember.groupMatchMember
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class GroupMatchMemberRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GroupMatchMemberRepositoryCustom {

    override fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean = queryFactory
        .selectOne()
        .from(groupMatchMember)
        .join(groupMatch).on(groupMatchMember.roomId.eq(groupMatch.id))
        .where(
            groupMatchMember.memberId.eq(memberId),
            groupMatch.quizSetId.eq(quizSetId),
        )
        .fetchFirst() != null
}
