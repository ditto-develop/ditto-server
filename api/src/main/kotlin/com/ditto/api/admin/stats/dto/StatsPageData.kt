package com.ditto.api.admin.stats.dto

import com.ditto.api.admin.stats.AgeBand
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.system.OperationWeek
import java.time.LocalDate

/** stats.js 가 읽는 페이지 데이터. 비율·구간 묶기는 JS 가 한다. */
data class StatsPageData(
    val weekStartedOn: LocalDate,
    val today: LocalDate,
    val genderLabels: Map<String, String>,
    val ageBandRanges: List<AgeBandRange>,
    val memberStats: MemberStatsData,
) {
    companion object {
        fun of(week: OperationWeek, today: LocalDate, memberStats: MemberStatsData): StatsPageData = StatsPageData(
            weekStartedOn = week.startedOn,
            today = today,
            genderLabels = Gender.entries.associate { it.name to it.description },
            ageBandRanges = AgeBand.entries.map { it.toRange() },
            memberStats = memberStats,
        )
    }
}

data class AgeBandRange(
    val label: String,
    val minAge: Int,
    val maxAge: Int,
)
