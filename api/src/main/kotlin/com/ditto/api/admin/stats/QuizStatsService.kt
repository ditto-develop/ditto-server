package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.QuizStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class QuizStatsService(
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val statsRoundFinder: StatsRoundFinder,
) {

    /** 활성 유저는 고른 회차까지 최근 4회차 안에 한 번이라도 완주한 회원이다. */
    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): QuizStatsData {
        val memberFilter = filter.toMemberFilter(today)
        val matchingType = filter.matchingType.targetType
        val activeRoundWeeks = statsRoundFinder.findRoundWeeksNewestFirst()
            .filter { it <= week.startedOn }
            .take(ACTIVE_ROUND_COUNT)
        return QuizStatsData(
            progressCounts = quizProgressRepository.countProgressesInWeek(week.startedOn, matchingType, memberFilter),
            activeRoundWeeks = activeRoundWeeks,
            activeMemberCount = quizProgressRepository.countMembersCompletedInWeeks(
                activeRoundWeeks,
                matchingType,
                memberFilter,
            ),
            choiceAnswerCounts = quizAnswerRepository.countAnswersPerChoiceInWeek(
                week.startedOn,
                matchingType,
                memberFilter,
            ),
        )
    }

    companion object {
        private const val ACTIVE_ROUND_COUNT = 4
    }
}
