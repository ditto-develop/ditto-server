package com.ditto.domain.memberreport

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.withId

object MemberReportFixture {

    fun create(
        reporterId: Long = 1L,
        reportedMemberId: Long = 2L,
        reason: MemberReportReason = MemberReportReason.INAPPROPRIATE_BEHAVIOR,
        source: MemberReportSource = MemberReportSource.PROFILE,
        detail: String? = null,
        id: Long = 0L,
    ): MemberReport = MemberReport.receive(
        reporterId = reporterId,
        reportedMemberId = reportedMemberId,
        reason = reason,
        source = source,
        detail = detail,
    ).withId(id)
}
