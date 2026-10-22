package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.entity.QMember.member
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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

    override fun countJoinedMembersByStatus(filter: MemberFilter): Map<MemberStatus, Long> {
        val countExpression = member.count()
        return queryFactory
            .select(member.status, countExpression)
            .from(member)
            .where(member.joinedAt.isNotNull, member.nonAdminMatching(filter))
            .groupBy(member.status)
            .fetch()
            .associate { row ->
                val status = row.get(member.status) ?: throw IllegalStateException("group by 키 status 가 비었다")
                status to (row.get(countExpression) ?: 0L)
            }
    }

    override fun findAllJoinedAtsWithin(
        joinedAtRange: OpenEndRange<LocalDateTime>,
        filter: MemberFilter,
    ): List<LocalDateTime> =
        queryFactory
            .select(member.joinedAt)
            .from(member)
            .where(
                member.joinedAt.goe(joinedAtRange.start),
                member.joinedAt.lt(joinedAtRange.endExclusive),
                member.nonAdminMatching(filter),
            )
            .fetch()
            .filterNotNull()

    override fun countCurrentMembersByGenderAndBirthDate(filter: MemberFilter): List<GenderBirthDateCount> {
        val countExpression = member.count()
        return queryFactory
            .select(member.gender, member.birthDate, countExpression)
            .from(member)
            .where(member.joinedAt.isNotNull, member.status.ne(MemberStatus.LEFT), member.nonAdminMatching(filter))
            .groupBy(member.gender, member.birthDate)
            .fetch()
            .map { row ->
                GenderBirthDateCount(
                    gender = row.get(member.gender),
                    birthDate = row.get(member.birthDate)?.toLocalDate(),
                    count = row.get(countExpression) ?: 0L,
                )
            }
    }
}
