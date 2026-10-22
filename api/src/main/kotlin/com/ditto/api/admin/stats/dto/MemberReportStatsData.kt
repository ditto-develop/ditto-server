package com.ditto.api.admin.stats.dto

import com.ditto.domain.memberreport.repository.querydsl.MemberReportCount

data class MemberReportStatsData(
    val memberReportCounts: List<MemberReportCount>,
)
