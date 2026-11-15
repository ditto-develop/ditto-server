package com.ditto.api.match

import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.system.OperationWeek
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** 시크릿 매칭 시각. 1차·2차 라운드의 요일과 시각을 여기서만 정한다. 마감 뒤 1:1 응시는 마지막 라운드 전까지 받는다. */
object SecretMatchingSchedule {

    private val ROUND_TIME: LocalTime = LocalTime.of(20, 0)
    private val ROUND_DAYS: Map<CandidateOrigin, DayOfWeek> = mapOf(
        CandidateOrigin.SECRET_FIRST to DayOfWeek.FRIDAY,
        CandidateOrigin.SECRET_SECOND to DayOfWeek.SATURDAY,
    )

    fun roundStartsAt(round: CandidateOrigin, week: OperationWeek): LocalDateTime {
        val roundDay = requireNotNull(ROUND_DAYS[round]) { "시크릿 매칭 라운드가 아니다: $round" }
        return week.startedOn.with(TemporalAdjusters.nextOrSame(roundDay)).atTime(ROUND_TIME)
    }

    fun lateSubmissionClosesAt(week: OperationWeek): LocalDateTime =
        roundStartsAt(CandidateOrigin.SECRET_SECOND, week)
}
