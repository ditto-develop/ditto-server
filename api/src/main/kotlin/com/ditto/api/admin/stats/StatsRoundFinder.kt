package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 회차는 활성 퀴즈셋이 있는 주다. */
@Service
class StatsRoundFinder(
    private val quizSetRepository: QuizSetRepository,
) {

    @Transactional(readOnly = true)
    fun findRecentRoundWeeksUntil(week: OperationWeek, count: Int): List<LocalDate> =
        quizSetRepository.findActiveQuizSetWeeksNewestFirst()
            .filter { it <= week.startedOn }
            .take(count)

    /** 고른 주가 회차가 아니면 비교할 회차가 없어 null 이다. 퀴즈를 쉰 주는 건너뛴다. */
    @Transactional(readOnly = true)
    fun findPreviousRoundWeek(week: OperationWeek): LocalDate? {
        val roundWeeksNewestFirst = quizSetRepository.findActiveQuizSetWeeksNewestFirst()
        if (week.startedOn !in roundWeeksNewestFirst) return null
        return roundWeeksNewestFirst.firstOrNull { it < week.startedOn }
    }

    /** 회차 번호는 회차를 오래된 순으로 센 값이다. 함께 넣은 주가 회차가 아니면 번호가 없다. */
    @Transactional(readOnly = true)
    fun findWeekOptionsIncluding(weeksToInclude: List<LocalDate>): List<StatsWeekOption> {
        val roundWeeksNewestFirst = quizSetRepository.findActiveQuizSetWeeksNewestFirst()
        val roundsByWeek = roundWeeksNewestFirst.reversed()
            .withIndex()
            .associate { (index, week) -> week to index + 1 }
        return (roundWeeksNewestFirst + weeksToInclude)
            .distinct()
            .sortedDescending()
            .map { StatsWeekOption(it, roundsByWeek[it]) }
    }

    companion object {
        // 기준 회차까지 이 수의 회차 안에서 한 번이라도 완주하면 활성 회원이다.
        const val ACTIVE_ROUND_COUNT = 4

        // 주별 신규 가입 표와 가입 주차별 리텐션이 고른 주까지 같은 8주를 본다.
        const val RECENT_WEEK_COUNT = 8

        fun recentWeeksRangeUntil(week: OperationWeek): OpenEndRange<LocalDateTime> {
            val firstWeek = week.minusWeeks(RECENT_WEEK_COUNT - 1L)
            return firstWeek.dateTimeRange.start..<week.dateTimeRange.endExclusive
        }
    }
}
