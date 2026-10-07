package com.ditto.api.admin.qa.dto

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.memberreport.entity.MemberReportStatus

class QaReportSection(
    val dummies: List<QaMember>,
    val targets: List<QaReportTarget>,
    val reports: List<QaReportRow>,
) {
    val hasDummies: Boolean = dummies.isNotEmpty()

    val hasRealTargets: Boolean = targets.any { !it.isDummy }

    val reasons: List<MemberReportReason> = MemberReportReason.entries

    val sources: List<MemberReportSource> = MemberReportSource.entries

    val recentReportLimit: Int = RECENT_REPORT_LIMIT

    /** 최근 건수 한도에 닿으면 더 있을 수 있다. */
    val reportCountText: String = if (reports.size >= RECENT_REPORT_LIMIT) "${reports.size}+" else "${reports.size}"

    val detailMaxLength: Int = MemberReport.DETAIL_MAX_LENGTH

    companion object {
        const val RECENT_REPORT_LIMIT = 20

        val EMPTY = QaReportSection(dummies = emptyList(), targets = emptyList(), reports = emptyList())
    }
}

class QaReportTarget(
    val member: QaMember,
    val isDummy: Boolean,
) {
    val optionLabel: String = if (isDummy) "${member.labelWithStatus} · 더미" else member.labelWithStatus
}

class QaReportRow(
    val reportId: Long,
    val reporter: QaMember,
    val reportedMember: QaMember,
    val reasonDescriptions: List<String>,
    val reportStatus: MemberReportStatus,
    val sanctionSummaryText: String?,
) {
    val isAwaitingReview: Boolean = reportStatus == MemberReportStatus.RECEIVED
}
