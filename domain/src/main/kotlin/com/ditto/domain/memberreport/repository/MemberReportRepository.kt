package com.ditto.domain.memberreport.repository

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface MemberReportRepository : JpaRepository<MemberReport, Long> {

    fun existsByReporterIdAndReportedMemberIdAndStatus(
        reporterId: Long,
        reportedMemberId: Long,
        status: MemberReportStatus,
    ): Boolean

    /** 일일 접수 상한 검사용 — 기준 시각 이후의 신고 수 */
    fun countByReporterIdAndCreatedAtGreaterThanEqual(reporterId: Long, createdAt: LocalDateTime): Long
}
