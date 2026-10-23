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
        val mostReportedFirstWeek = week.minusWeeks(MOST_REPORTED_WEEK_COUNT - 1L)
        val receivedRange = mostReportedFirstWeek.dateTimeRange.start..<week.dateTimeRange.endExclusive
        return MemberReportStatsData(
            memberReportCounts = memberReportRepository.countMemberReportsReceivedInWeek(
                week.startedOn,
                filter.matchingType.quizMatchingType,
                memberFilter,
            ),
            mostReportedMembers =
                memberReportRepository.findMostReportedMembers(receivedRange, memberFilter, MOST_REPORTED_MEMBER_LIMIT),
            mostReportedFirstWeek = mostReportedFirstWeek.startedOn,
        )
    }

    companion object {
        private const val MOST_REPORTED_WEEK_COUNT = 4

        // 2건 이상만 보이는 건 화면이 거른다.
        private const val MOST_REPORTED_MEMBER_LIMIT = 10L
    }
}
