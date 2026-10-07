package com.ditto.api.admin.qa.dto

import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.memberreport.entity.MemberReportStatus

class QaReportSection(
    val dummies: List<QaMember>,
    val targets: List<QaReportTarget>,
    val reports: List<QaReportRow>,
) {
    val reasons: List<MemberReportReason> = MemberReportReason.entries

    val sources: List<MemberReportSource> = MemberReportSource.entries

    val recentReportLimit: Int = RECENT_REPORT_LIMIT

    companion object {
        const val RECENT_REPORT_LIMIT = 20

        val EMPTY = QaReportSection(dummies = emptyList(), targets = emptyList(), reports = emptyList())
    }
}

class QaReportTarget(
    val member: QaMember,
    val isDummy: Boolean,
)

class QaReportRow(
    val reportId: Long,
    val reporter: QaMember,
    val reportedMember: QaMember,
    val reasonDescriptions: List<String>,
    val status: MemberReportStatus,
) {
    val isAwaitingReview: Boolean = status == MemberReportStatus.RECEIVED
}
