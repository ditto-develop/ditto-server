package com.ditto.api.system

import java.time.DayOfWeek

/**
 * 요일 기반 서비스 기간 상태.
 * - 월~수: 퀴즈 풀기([QUIZ_PERIOD])
 * - 목: 매칭([MATCHING_PERIOD])
 * - 금~일: 채팅([CHATTING_PERIOD])
 */
enum class SystemPeriod {
    QUIZ_PERIOD,
    MATCHING_PERIOD,
    CHATTING_PERIOD,
    ;

    companion object {
        fun from(dayOfWeek: DayOfWeek): SystemPeriod = when (dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> QUIZ_PERIOD
            DayOfWeek.THURSDAY -> MATCHING_PERIOD
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> CHATTING_PERIOD
        }
    }
}
