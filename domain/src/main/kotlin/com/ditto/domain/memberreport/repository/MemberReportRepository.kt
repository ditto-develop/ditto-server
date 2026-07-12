package com.ditto.domain.memberreport.repository

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MemberReportRepository : JpaRepository<MemberReport, Long> {

    fun existsByReporterIdAndReportedMemberIdAndStatus(
        reporterId: Long,
        reportedMemberId: Long,
        status: MemberReportStatus,
    ): Boolean
}
