package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.entity.QMember.member
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class MemberRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : MemberRepositoryCustom {

    override fun findAllIdsByStatus(status: MemberStatus): List<Long> =
        queryFactory
            .select(member.id)
            .from(member)
            .where(member.status.eq(status))
            .fetch()
}
