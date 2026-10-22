package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 회차는 활성 퀴즈셋이 있는 주다. */
@Service
class StatsRoundFinder(
    private val quizSetRepository: QuizSetRepository,
) {

    @Transactional(readOnly = true)
    fun findRecentRoundWeeksUntil(week: OperationWeek, count: Int): List<LocalDate> =
        quizSetRepository.findActiveWeekStartedOnsNewestFirst()
            .filter { it <= week.startedOn }
            .take(count)

    /** 고른 주가 회차가 아니면 비교할 회차가 없어 null 이다. 퀴즈를 쉰 주는 건너뛴다. */
    @Transactional(readOnly = true)
    fun findPreviousRoundWeek(week: OperationWeek): LocalDate? {
        val roundWeeksNewestFirst = quizSetRepository.findActiveWeekStartedOnsNewestFirst()
        if (week.startedOn !in roundWeeksNewestFirst) return null
        return roundWeeksNewestFirst.firstOrNull { it < week.startedOn }
    }

    /** 회차 번호는 회차를 오래된 순으로 센 값이다. 함께 넣은 주가 회차가 아니면 번호가 없다. */
    @Transactional(readOnly = true)
    fun findWeekOptionsIncluding(weeksToInclude: List<LocalDate>): List<StatsWeekOption> {
        val roundWeeksNewestFirst = quizSetRepository.findActiveWeekStartedOnsNewestFirst()
        val roundsByWeek = roundWeeksNewestFirst.reversed()
            .withIndex()
            .associate { (index, week) -> week to index + 1 }
        return (roundWeeksNewestFirst + weeksToInclude)
            .distinct()
            .sortedDescending()
            .map { StatsWeekOption(it, roundsByWeek[it]) }
    }
}
