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
    fun findStats(filter: StatsFilter, week: OperationWeek, today: LocalDate): MemberReportStatsData =
        MemberReportStatsData(
            memberReportCounts = memberReportRepository.countReceivedInWeek(
                week.startedOn,
                filter.matchingType.targetType,
                filter.toMemberFilter(today),
            ),
        )
}
