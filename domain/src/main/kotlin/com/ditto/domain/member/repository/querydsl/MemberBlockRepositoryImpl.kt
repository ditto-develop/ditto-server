package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.QMemberBlock.memberBlock
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

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
}
