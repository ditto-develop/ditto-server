package com.ditto.api.match

import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** 시크릿 매칭(IMP-2) 시각. 금·토 20:00 에 1:1 추가 라운드가 돌고, 마감 뒤 1:1 응시는 토요일 라운드 전까지 받는다. */
object SecretMatchingSchedule {

    private val ROUND_TIME: LocalTime = LocalTime.of(20, 0)
    private const val SATURDAY_OFFSET_DAYS = 5L

    fun lateSubmissionClosesAt(week: OperationWeek): LocalDateTime =
        week.startedOn.plusDays(SATURDAY_OFFSET_DAYS).atTime(ROUND_TIME)
}
