package com.ditto.domain.system

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * 운영 주(월요일~일요일)를 식별하는 값 객체. 식별 키는 그 주 월요일 날짜(startedOn) 하나이며,
 * 월·연 경계와 무관하게 같은 월요일~일요일은 같은 주로 취급한다. year/month/weekOfMonth는 표시용 파생값.
 */
@JvmInline
value class OperationWeek(val startedOn: LocalDate) {

    init {
        require(startedOn.dayOfWeek == DayOfWeek.MONDAY) { "운영 주 시작일은 월요일이어야 합니다: $startedOn" }
    }

    val year: Int
        get() = startedOn.year

    val month: Int
        get() = startedOn.monthValue

    val weekOfMonth: Int
        get() = startedOn.get(WEEK_FIELDS.weekOfMonth())

    companion object {
        private val WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 1)

        fun including(date: LocalDate): OperationWeek = OperationWeek(mondayOf(date))

        private fun mondayOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
