package com.ditto.api.admin.support

import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * 어드민 화면용 주차 표기 헬퍼. `2026-06 3/4주차`(연-월 + 현재주차/그 달 총주차) 형식으로 보여준다.
 * 주차 계산은 시스템 상태와 동일한 기준(월요일 시작·1-base 월 내 주차)을 쓴다.
 * Thymeleaf 에서 `${@weekLabel.of(year, month, week)}` 로 호출.
 */
@Component("weekLabel")
class WeekLabel {
    private val weekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

    /** 해당 연·월의 총 주차 수(말일의 weekOfMonth). */
    fun totalWeeks(year: Int, month: Int): Int =
        LocalDate.of(year, month, 1)
            .with(TemporalAdjusters.lastDayOfMonth())
            .get(weekFields.weekOfMonth())

    /** 예: of(2026, 6, 3) -> "2026-06 3/4주차" */
    fun of(year: Int, month: Int, week: Int): String =
        "%04d-%02d %d/%d주차".format(year, month, week, totalWeeks(year, month))
}
