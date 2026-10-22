package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsWeekOption
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class StatsWeekOptionFinder(
    private val quizSetRepository: QuizSetRepository,
) {

    /** 회차 번호는 퀴즈셋이 있는 주를 오래된 순으로 센 값이다. 함께 넣은 주에 퀴즈셋이 없으면 번호가 없다. */
    @Transactional(readOnly = true)
    fun findWeekOptionsIncluding(weeksToInclude: List<LocalDate>): List<StatsWeekOption> {
        val roundWeeksNewestFirst = quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc()
            .map { it.weekStartedOn }
            .distinct()
        val roundsByWeek = roundWeeksNewestFirst.reversed()
            .withIndex()
            .associate { (index, week) -> week to index + 1 }
        return (roundWeeksNewestFirst + weeksToInclude)
            .distinct()
            .sortedDescending()
            .map { StatsWeekOption(it, roundsByWeek[it]) }
    }
}
