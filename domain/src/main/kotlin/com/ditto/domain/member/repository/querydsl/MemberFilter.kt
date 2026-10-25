package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.QMember
import com.querydsl.core.types.dsl.BooleanExpression
import java.time.LocalDate
import java.time.LocalDateTime

/** 집계 쿼리가 회원을 좁히는 조건. null 인 항목은 거르지 않고, 관리자 계정은 includesAdmins 를 켤 때만 센다. */
data class MemberFilter(
    val gender: Gender? = null,
    val birthDateRange: ClosedRange<LocalDate>? = null,
    val joinedAtRange: OpenEndRange<LocalDateTime>? = null,
    val includesAdmins: Boolean = false,
) {
    companion object {
        val NONE = MemberFilter()
    }
}

internal fun QMember.matchesFilter(filter: MemberFilter): BooleanExpression =
    roleCondition(filter.includesAdmins)
        .and(filter.gender?.let { gender.eq(it) })
        .and(filter.birthDateRange?.let { bornWithin(it) })
        .and(filter.joinedAtRange?.let { joinedAt.goe(it.start).and(joinedAt.lt(it.endExclusive)) })

// 관리자를 넣을 때도 뒤 조건을 and 로 이을 시작 식이 필요하다. role 은 not null 이라 isNotNull 은 늘 참이다.
private fun QMember.roleCondition(includesAdmins: Boolean): BooleanExpression =
    if (includesAdmins) role.isNotNull else role.eq(MemberRole.USER)

// birth_date 는 그날 00:00 으로 저장된다.
private fun QMember.bornWithin(range: ClosedRange<LocalDate>): BooleanExpression =
    birthDate.goe(range.start.atStartOfDay())
        .and(birthDate.lt(range.endInclusive.plusDays(1).atStartOfDay()))
