package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.QMember
import com.querydsl.core.types.dsl.BooleanExpression
import java.time.LocalDate
import java.time.LocalDateTime

/** 집계 쿼리가 회원을 좁히는 조건. null 인 항목은 거르지 않는다. */
data class MemberFilter(
    val gender: Gender? = null,
    val birthDateRange: ClosedRange<LocalDate>? = null,
    val joinedAtRange: OpenEndRange<LocalDateTime>? = null,
) {
    companion object {
        val NONE = MemberFilter()
    }
}

internal fun QMember.nonAdminMatching(filter: MemberFilter): BooleanExpression =
    role.eq(MemberRole.USER)
        .and(filter.gender?.let { gender.eq(it) })
        .and(filter.birthDateRange?.let { bornWithin(it) })
        .and(filter.joinedAtRange?.let { joinedAt.goe(it.start).and(joinedAt.lt(it.endExclusive)) })

// birth_date 는 그날 00:00 으로 저장된다.
private fun QMember.bornWithin(range: ClosedRange<LocalDate>): BooleanExpression =
    birthDate.goe(range.start.atStartOfDay())
        .and(birthDate.lt(range.endInclusive.plusDays(1).atStartOfDay()))
