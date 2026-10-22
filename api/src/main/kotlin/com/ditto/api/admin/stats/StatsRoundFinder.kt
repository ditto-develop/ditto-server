package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 회차는 활성 퀴즈셋이 있는 주다. 번호는 그런 주를 오래된 순으로 센 값이다. */
@Service
class StatsRoundFinder(
    private val quizSetRepository: QuizSetRepository,
) {

    @Transactional(readOnly = true)
    fun findRoundWeeksNewestFirst(): List<LocalDate> =
        quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc()
            .filter { it.isActive }
            .map { it.weekStartedOn }
            .distinct()

    /** 함께 넣은 주가 회차가 아니면 번호 없이 들어간다. */
    @Transactional(readOnly = true)
    fun findWeekOptionsIncluding(weeksToInclude: List<LocalDate>): List<StatsWeekOption> {
        val roundWeeksNewestFirst = findRoundWeeksNewestFirst()
        val roundsByWeek = roundWeeksNewestFirst.reversed()
            .withIndex()
            .associate { (index, week) -> week to index + 1 }
        return (roundWeeksNewestFirst + weeksToInclude)
            .distinct()
            .sortedDescending()
            .map { StatsWeekOption(it, roundsByWeek[it]) }
    }
}
