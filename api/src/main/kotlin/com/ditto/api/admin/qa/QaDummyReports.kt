package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaDummyReport
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.admin.qa.dto.QaReportTarget
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.domain.memberreport.repository.MemberReportRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 콘솔의 더미로 신고하기 카드. 신고는 앱 API 로 내고, 여기서는 고를 대상과 더미가 낸 신고를 모은다. */
@Component
@Transactional(readOnly = true)
class QaDummyReports(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val memberReportRepository: MemberReportRepository,
) {
    fun composeSection(rooms: List<QaRoomSummary>): QaReportSection {
        val dummyIds = qaDummies.findIds().sorted()
        if (dummyIds.isEmpty()) return QaReportSection(emptyList(), emptyList(), emptyList(), RECENT_REPORT_LIMIT)

        val realMembers = rooms.flatMap { it.realMembers }.distinctBy { it.id }
        val reports = memberReportRepository.findByReporterIdInOrderByIdDesc(dummyIds, Limit.of(RECENT_REPORT_LIMIT))
        val members = qaMemberLabels.load(dummyIds + reports.map { it.reportedMemberId })
        val dummies = dummyIds.map(members::of)

        return QaReportSection(
            dummies = dummies,
            targets = realMembers.map { QaReportTarget(it, isDummy = false) } +
                dummiesExceptDefaultReporter(dummies).map { QaReportTarget(it, isDummy = true) },
            reports = reports.map { report ->
                QaDummyReport(
                    reportId = report.id,
                    reporter = members.of(report.reporterId),
                    reported = members.of(report.reportedMemberId),
                    reasonDescriptions = report.reasons.sortedBy { it.ordinal }.map { it.description },
                    status = report.status,
                )
            },
            recentLimit = RECENT_REPORT_LIMIT,
        )
    }

    /** 실회원이 없으면 첫 대상이 기본 신고자와 같아 자기 신고로 거부된다. 기본 신고자를 대상 맨 뒤로 보낸다. */
    private fun dummiesExceptDefaultReporter(dummies: List<QaMember>): List<QaMember> =
        dummies.drop(1) + dummies.take(1)

    companion object {
        private const val RECENT_REPORT_LIMIT = 20
    }
}
