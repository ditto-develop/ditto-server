package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.MemberStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberStatsService(
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val statsRoundFinder: StatsRoundFinder,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MemberStatsData {
        val memberFilter = filter.toMemberFilter(today)
        // 계정 상태는 지금 상태밖에 몰라서 활성도 고른 회차가 아니라 지금 회차로 센다.
        val currentActiveRoundWeeks = statsRoundFinder.findRecentRoundWeeksUntil(
            OperationWeek.containing(today),
            StatsRoundFinder.ACTIVE_ROUND_COUNT,
        )
        return MemberStatsData(
            countsByStatus = memberRepository.countMembersByStatus(memberFilter),
            currentActiveRoundWeeks = currentActiveRoundWeeks,
            recentlyCompletedActiveStatusCount =
                quizProgressRepository.countActiveStatusMembersCompletedInWeeks(currentActiveRoundWeeks, memberFilter),
            recentWeekCount = RECENT_WEEK_COUNT,
            recentJoinedAts = memberRepository.findAllJoinedAtsWithin(recentWeeksRangeUntil(week), memberFilter),
            genderBirthDateCounts = memberRepository.countCurrentMembersByGenderAndBirthDate(memberFilter),
            locationCounts = memberRepository.countCurrentMembersByLocation(memberFilter),
            leaveReasonCounts = memberRepository.countMembersLeftWithinByLeaveReason(week.dateTimeRange, memberFilter),
            adminMemberCount = memberRepository.countByRoleAndStatusNot(MemberRole.ADMIN, MemberStatus.LEFT),
        )
    }

    companion object {
        // 주별 신규 가입 표와 가입 주차별 리텐션이 같은 8주를 본다.
        const val RECENT_WEEK_COUNT = 8

        fun recentWeeksRangeUntil(week: OperationWeek): OpenEndRange<LocalDateTime> {
            val firstWeek = week.minusWeeks(RECENT_WEEK_COUNT - 1L)
            return firstWeek.dateTimeRange.start..<week.dateTimeRange.endExclusive
        }
    }
}
