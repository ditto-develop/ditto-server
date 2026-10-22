package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.MemberStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberStatsService(
    private val memberRepository: MemberRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MemberStatsData {
        val memberFilter = filter.toMemberFilter(today)
        return MemberStatsData(
            countsByStatus = memberRepository.countJoinedMembersByStatus(memberFilter),
            recentWeekCount = RECENT_WEEK_COUNT,
            recentJoinedAts = memberRepository.findAllJoinedAtsWithin(recentWeeksRangeUntil(week), memberFilter),
            genderBirthDateCounts = memberRepository.countCurrentMembersByGenderAndBirthDate(memberFilter),
        )
    }

    private fun recentWeeksRangeUntil(week: OperationWeek): OpenEndRange<LocalDateTime> {
        val firstWeek = week.minusWeeks(RECENT_WEEK_COUNT - 1L)
        return firstWeek.dateTimeRange.start..<week.dateTimeRange.endExclusive
    }

    companion object {
        private const val RECENT_WEEK_COUNT = 8
    }
}
