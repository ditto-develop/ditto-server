package com.ditto.domain.quiz.entity

import com.ditto.domain.system.OperationWeek
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 한 운영 주 안에서 퀴즈 응답을 받는 고정 기간 — 월요일 00:00:00부터 수요일 23:59:59까지.
 *
 * 목요일 05:00 매칭 배치가 `endDate < now`로 마감을 판단하고 금요일에 채팅방이 열리는 주간 흐름에 맞춘 값이다.
 * 어드민이 일시를 직접 입력하다 생기는 실수를 없애기 위해 주차만 받고 기간은 여기서 만든다.
 *
 * 종료를 `LocalTime.MAX`(23:59:59.999999999)로 두지 않는 이유: DATETIME(6) 컬럼에 저장되며 반올림돼
 * 목요일 00:00:00이 될 수 있다.
 */
data class QuizResponsePeriod(val week: OperationWeek) {

    val startsAt: LocalDateTime
        get() = week.startedOn.atStartOfDay()

    val endsAt: LocalDateTime
        get() = week.startedOn.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)).atTime(LAST_SECOND_OF_DAY)

    companion object {
        private val LAST_SECOND_OF_DAY: LocalTime = LocalTime.of(23, 59, 59)
    }
}
