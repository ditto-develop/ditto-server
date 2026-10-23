package com.ditto.api.admin.stats.dto

import com.ditto.domain.memberreport.repository.querydsl.MemberReportCount
import com.ditto.domain.memberreport.repository.querydsl.ReportedMemberCount
import java.time.LocalDate

data class MemberReportStatsData(
    val memberReportCounts: List<MemberReportCount>,
    val mostReportedMembers: List<ReportedMemberCount>,
    val mostReportedFirstWeek: LocalDate,
)
