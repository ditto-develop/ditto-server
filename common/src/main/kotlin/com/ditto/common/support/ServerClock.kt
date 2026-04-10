package com.ditto.common.support

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields

class ServerClock(
    private val clock: Clock = Clock.system(KST),
) {
    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    fun currentWeekInfo(): WeekInfo {
        val today = today()
        return WeekInfo(
            year = today.year,
            month = today.monthValue,
            week = today.get(WEEK_FIELDS.weekOfMonth()),
        )
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private val WEEK_FIELDS = WeekFields.of(DayOfWeek.MONDAY, 1)
    }
}

data class WeekInfo(
    val year: Int,
    val month: Int,
    val week: Int,
)
