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

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): QuizStatsData {
        val memberFilter = filter.toMemberFilter(today)
        val quizMatchingType = filter.matchingType.quizMatchingType
        val activeRoundWeeks = statsRoundFinder.findRecentRoundWeeksUntil(week, ACTIVE_ROUND_COUNT)
        return QuizStatsData(
            progressCounts = quizProgressRepository.countProgressesInWeek(
                week.startedOn,
                quizMatchingType,
                memberFilter,
            ),
            activeRoundCount = ACTIVE_ROUND_COUNT,
            activeRoundWeeks = activeRoundWeeks,
            activeMemberCount = quizProgressRepository.countMembersCompletedInWeeks(
                activeRoundWeeks,
                quizMatchingType,
                memberFilter,
            ),
            choiceAnswerCounts = quizAnswerRepository.countAnswersPerChoiceInWeek(
                week.startedOn,
                quizMatchingType,
                memberFilter,
            ),
        )
    }

    companion object {
        // 활성 유저는 고른 회차까지 최근 이만큼의 회차 안에 한 번이라도 완주한 회원이다.
        private const val ACTIVE_ROUND_COUNT = 4
    }
}
