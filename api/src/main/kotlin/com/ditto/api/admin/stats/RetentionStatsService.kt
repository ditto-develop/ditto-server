package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.RetentionStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 지난 회차는 고른 회차 바로 앞의 회차다. 퀴즈를 쉰 주는 건너뛴다. */
@Service
class RetentionStatsService(
    private val quizProgressRepository: QuizProgressRepository,
    private val statsRoundFinder: StatsRoundFinder,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): RetentionStatsData {
        val previousRoundWeek = statsRoundFinder.findRoundWeeksNewestFirst().firstOrNull { it < week.startedOn }
            ?: return RetentionStatsData.NO_PREVIOUS_ROUND
        val memberFilter = filter.toMemberFilter(today)
        val targetType = filter.matchingType.targetType
        return RetentionStatsData(
            previousRoundWeek = previousRoundWeek,
            previousProgressCounts = quizProgressRepository.countProgressesInWeek(
                previousRoundWeek,
                targetType,
                memberFilter,
            ),
            completedAgainCounts = quizProgressRepository
                .countMembersCompletedAgain(previousRoundWeek, week.startedOn, memberFilter)
                .filter { targetType == null || it.matchingType == targetType },
        )
    }
}
