package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.RetentionStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RetentionStatsService(
    private val quizProgressRepository: QuizProgressRepository,
    private val statsRoundFinder: StatsRoundFinder,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): RetentionStatsData {
        val previousRoundWeek = statsRoundFinder.findPreviousRoundWeek(week)
            ?: return RetentionStatsData.NO_PREVIOUS_ROUND
        val memberFilter = filter.toMemberFilter(today)
        val quizMatchingType = filter.matchingType.quizMatchingType
        return RetentionStatsData(
            previousRoundWeek = previousRoundWeek,
            previousProgressCounts = quizProgressRepository.countProgressesInWeek(
                previousRoundWeek,
                quizMatchingType,
                memberFilter,
            ),
            completedAgainCounts = quizProgressRepository
                .countMembersCompletedAgain(previousRoundWeek, week.startedOn, memberFilter)
                .filter { quizMatchingType == null || it.previousMatchingType == quizMatchingType },
        )
    }
}
