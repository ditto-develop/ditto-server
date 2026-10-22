package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.AgeBandRange
import java.time.LocalDate

/** 운영 지표의 연령대. 오늘 기준 만 나이로 나눈다. */
enum class AgeBand(
    val description: String,
    val ages: IntRange,
) {
    EARLY_20S("20대 초반", 20..23),
    MID_20S("20대 중반", 24..26),
    LATE_20S("20대 후반", 27..29),
    THIRTIES("30대", 30..39),
    ;

    fun birthDateRange(today: LocalDate): ClosedRange<LocalDate> =
        today.minusYears(ages.last + 1L).plusDays(1)..today.minusYears(ages.first.toLong())

    fun toRange(): AgeBandRange = AgeBandRange(description, ages.first, ages.last)
}
