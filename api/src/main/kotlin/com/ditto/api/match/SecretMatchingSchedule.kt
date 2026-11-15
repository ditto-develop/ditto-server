package com.ditto.api.match

import com.ditto.domain.system.OperationWeek
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** 시크릿 매칭 시각. 마감 뒤 1:1 응시는 토요일 20:00 라운드 전까지 받는다. */
object SecretMatchingSchedule {

    private val ROUND_TIME: LocalTime = LocalTime.of(20, 0)

    fun lateSubmissionClosesAt(week: OperationWeek): LocalDateTime =
        week.startedOn.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(ROUND_TIME)
}
