package com.ditto.api.admin.stats.dto

import com.ditto.domain.memberreport.repository.querydsl.MemberReportCount
import com.ditto.domain.memberreport.repository.querydsl.ReportedMemberCount

data class MemberReportStatsData(
    val memberReportCounts: List<MemberReportCount>,
    val mostReportedMembers: List<ReportedMemberCount>,
)
