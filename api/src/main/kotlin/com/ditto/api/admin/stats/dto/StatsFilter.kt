package com.ditto.api.admin.stats.dto

import com.ditto.api.admin.stats.AgeBand
import com.ditto.api.admin.stats.MatchingTypeFilter
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.system.OperationWeek
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

/** 운영 지표 필터 쿼리 파라미터. 회차·가입 주차는 그 주의 아무 날이나 받는다. */
data class StatsFilter(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val week: LocalDate? = null,
    val matchingType: MatchingTypeFilter = MatchingTypeFilter.ALL,
    val gender: Gender? = null,
    val ageBand: AgeBand? = null,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val joinedWeek: LocalDate? = null,
    val includeAdmins: Boolean = false,
) {
    fun selectedWeek(currentWeek: OperationWeek): OperationWeek =
        week?.let { OperationWeek.containing(it) } ?: currentWeek

    fun hasConditions(): Boolean = hasMatchingTypeCondition() || hasMemberConditions()

    fun hasMatchingTypeCondition(): Boolean = matchingType != MatchingTypeFilter.ALL

    fun hasMemberConditions(): Boolean = gender != null || ageBand != null || joinedWeek != null

    fun toMemberFilter(today: LocalDate): MemberFilter = MemberFilter(
        gender = gender,
        birthDateRange = ageBand?.birthDateRange(today),
        joinedAtRange = joinedWeek?.let { OperationWeek.containing(it).dateTimeRange },
        includesAdmins = includeAdmins,
    )
}
