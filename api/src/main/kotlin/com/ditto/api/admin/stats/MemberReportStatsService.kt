package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.MemberReportStatsData
import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.system.OperationWeek
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MemberReportStatsService(
    private val memberReportRepository: MemberReportRepository,
) {

    @Transactional(readOnly = true)
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MemberReportStatsData {
        val memberFilter = filter.toMemberFilter(today)
        return MemberReportStatsData(
            memberReportCounts = memberReportRepository.countMemberReportsReceivedInWeek(
                week.startedOn,
                filter.matchingType.quizMatchingType,
                memberFilter,
            ),
            mostReportedMembers = memberReportRepository.findMostReportedMembers(
                week.minusWeeks(MOST_REPORTED_WEEK_COUNT - 1L).dateTimeRange.start..<week.dateTimeRange.endExclusive,
                memberFilter,
                MOST_REPORTED_MEMBER_LIMIT,
            ),
        )
    }

    // 2건 이상만 보이는 건 화면이 거른다.
    companion object {
        private const val MOST_REPORTED_WEEK_COUNT = 4
        private const val MOST_REPORTED_MEMBER_LIMIT = 10L
    }
}
