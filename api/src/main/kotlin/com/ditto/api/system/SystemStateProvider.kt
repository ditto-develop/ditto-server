package com.ditto.api.system

import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.WeekFields

/** 서버 현재 시각 기준의 (연/월/주차/기간) 상태. */
data class SystemState(
    val year: Int,
    val month: Int,
    val week: Int,
    val period: SystemPeriod,
    val dateTime: LocalDateTime,
)

/**
 * 서버 현재 시각([ServerTimeProvider]) 으로부터 시스템 상태를 계산한다.
 * 주차는 월요일 시작·1-base 기준 월 내 주차([WeekFields]).
 */
@Component
class SystemStateProvider(
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun current(): SystemState {
        val now = serverTimeProvider.now()
        val weekOfMonth = now.toLocalDate().get(WEEK_FIELDS.weekOfMonth())
        return SystemState(
            year = now.year,
            month = now.monthValue,
            week = weekOfMonth,
            period = SystemPeriod.from(now.dayOfWeek),
            dateTime = now,
        )
    }

    companion object {
        private val WEEK_FIELDS: WeekFields = WeekFields.of(DayOfWeek.MONDAY, 1)
    }
}
