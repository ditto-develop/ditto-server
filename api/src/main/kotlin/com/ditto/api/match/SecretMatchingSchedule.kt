package com.ditto.api.match

import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.system.OperationWeek
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 시크릿 매칭 라운드 시각. 라운드 풀·늦은 응시 마감은 이 시각으로 판단한다.
 * cron(`MatchingScheduler.generateSecretRound`)은 이 시각에 맞춰 따로 적혀 있어 바꿀 때 함께 고친다.
 */
object SecretMatchingSchedule {

    private val ROUND_TIME: LocalTime = LocalTime.of(20, 0)
    private val ROUND_DAYS: Map<CandidateOrigin, DayOfWeek> = mapOf(
        CandidateOrigin.SECRET_FIRST to DayOfWeek.FRIDAY,
        CandidateOrigin.SECRET_SECOND to DayOfWeek.SATURDAY,
    )

    /** [secretRound]는 SECRET_FIRST·SECRET_SECOND 만 받는다. */
    fun roundStartsAt(secretRound: CandidateOrigin, week: OperationWeek): LocalDateTime {
        val roundDay = requireNotNull(ROUND_DAYS[secretRound]) { "시크릿 매칭 라운드가 아니다: $secretRound" }
        return week.startedOn.with(TemporalAdjusters.nextOrSame(roundDay)).atTime(ROUND_TIME)
    }

    /** [now]가 속한 주에서 시각이 지난 가장 늦은 라운드. 1차 라운드 전이면 null 이다. */
    fun dueRoundAt(now: LocalDateTime): Pair<OperationWeek, CandidateOrigin>? {
        val week = OperationWeek.containing(now.toLocalDate())
        val dueRound = ROUND_DAYS.keys
            .associateWith { roundStartsAt(it, week) }
            .filterValues { it <= now }
            .maxByOrNull { it.value }
            ?.key
            ?: return null
        return week to dueRound
    }

    /** [week]에서 [time]보다 늦은 첫 라운드 시각. [time]과 같은 시각의 라운드는 넣지 않는다. 남은 라운드가 없으면 null 이다. */
    fun nextRoundAfter(week: OperationWeek, time: LocalDateTime): LocalDateTime? =
        ROUND_DAYS.keys
            .map { roundStartsAt(it, week) }
            .filter { it > time }
            .minOrNull()

    fun lateSubmissionClosesAt(week: OperationWeek): LocalDateTime =
        roundStartsAt(CandidateOrigin.SECRET_SECOND, week)
}
