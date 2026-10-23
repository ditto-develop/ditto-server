package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.MatchStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.querydsl.UnmatchedMember
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// 매칭 유형은 화면이 그 유형 줄만 골라 그린다. 테이블마다 유형이 하나라 쿼리로 거를 게 없다.
// 연속 미매칭은 회원 목록이라 유형을 쿼리로 건다.
@Service
class MatchStatsService(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val statsRoundFinder: StatsRoundFinder,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MatchStatsData {
        val memberFilter = filter.toMemberFilter(today)
        val unmatchedRoundWeeks = statsRoundFinder.findRecentRoundWeeksUntil(week, UNMATCHED_ROUND_COUNT)
        return MatchStatsData(
            matchCandidateCounts = matchCandidateRepository.countCandidateOwnersInWeek(week.startedOn, memberFilter),
            candidateCountsPerOwner =
                matchCandidateRepository.countCandidatesPerOwnerInWeek(week.startedOn, memberFilter),
            personalMatchMemberCounts =
                personalMatchRepository.countAcceptedMembersInWeek(week.startedOn, memberFilter),
            groupMatchMemberCounts =
                groupMatchMemberRepository.countInvitedMembersInWeek(week.startedOn, memberFilter),
            pairScores = matchCandidateRepository.findPairScoresInWeek(week.startedOn),
            acceptedPairScores = matchCandidateRepository.findAcceptedPairScoresInWeek(week.startedOn),
            groupScores = groupMatchRepository.findGroupScoresInWeek(week.startedOn),
            activeGroupMemberCounts =
                groupMatchMemberRepository.countAcceptedMembersPerActiveGroupInWeek(week.startedOn),
            unmatchedRoundWeeks = unmatchedRoundWeeks,
            membersUnmatchedInEveryRound = findMembersUnmatchedInEveryRound(unmatchedRoundWeeks, filter, memberFilter),
        )
    }

    // 회차가 모자라면 한 회차만 보고 연속이라고 할 수 없어 비운다.
    private fun findMembersUnmatchedInEveryRound(
        roundWeeks: List<LocalDate>,
        filter: StatsFilter,
        memberFilter: MemberFilter,
    ): List<UnmatchedMember> {
        if (roundWeeks.size < UNMATCHED_ROUND_COUNT) return emptyList()
        return quizProgressRepository.findMembersUnmatchedInEveryRound(
            roundWeeks,
            filter.matchingType.quizMatchingType,
            memberFilter,
        )
    }

    companion object {
        private const val UNMATCHED_ROUND_COUNT = 2
    }
}
