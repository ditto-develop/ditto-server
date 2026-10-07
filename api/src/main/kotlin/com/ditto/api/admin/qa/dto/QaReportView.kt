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
