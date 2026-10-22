package com.ditto.domain.memberreport.repository.querydsl

import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportStatus

data class MemberReportCount(
    val reasons: Set<MemberReportReason>,
    val status: MemberReportStatus,
    val count: Long,
)
