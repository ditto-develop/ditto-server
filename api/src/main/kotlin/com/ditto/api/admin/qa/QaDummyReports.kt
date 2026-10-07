package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaReportRow
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.admin.qa.dto.QaReportTarget
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.domain.memberreport.entity.MemberReport
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
    fun composeSection(console: QaConsoleView, rooms: List<QaRoomSummary>): QaReportSection {
        val dummyIds = qaDummies.findIds().sorted()
        if (dummyIds.isEmpty()) return QaReportSection.EMPTY

        val reports = memberReportRepository.findByReporterIdInOrderByIdDesc(
            dummyIds,
            Limit.of(QaReportSection.RECENT_REPORT_LIMIT),
        )
        val members = qaMemberLabels.load(dummyIds + reports.map { it.reportedMemberId })
        val dummies = dummyIds.map(members::of)
        val realMembers = realMembersOnConsole(console, rooms).filterNot { it.id in dummyIds }

        return QaReportSection(
            dummies = dummies,
            targets = realMembers.map { QaReportTarget(it, isDummy = false) } +
                dummiesWithDefaultReporterLast(dummies).map { QaReportTarget(it, isDummy = true) },
            reports = reports.map { it.toRow(members) },
        )
    }

    /** 테스트 계정이 아직 방에 없어도 고를 수 있게, 콘솔 어디에든 나온 실회원을 모은다. */
    private fun realMembersOnConsole(console: QaConsoleView, rooms: List<QaRoomSummary>): List<QaMember> =
        (
            rooms.flatMap { it.realMembers } +
                console.personal.receivedRequests.map { it.requester } +
                console.personal.sentRequests.map { it.receiver } +
                console.personal.requestOptions.map { it.receiver } +
                console.group.groups.flatMap { group -> group.members.filterNot { it.isDummy }.map { it.member } }
            ).distinctBy { it.id }

    /** 실회원이 없으면 첫 대상이 기본 신고자와 같아 자기 신고로 거부된다. */
    private fun dummiesWithDefaultReporterLast(dummies: List<QaMember>): List<QaMember> =
        dummies.drop(1) + dummies.take(1)

    private fun MemberReport.toRow(members: QaMembers) = QaReportRow(
        reportId = id,
        reporter = members.of(reporterId),
        reportedMember = members.of(reportedMemberId),
        reasonDescriptions = reasons.sortedBy { it.ordinal }.map { it.description },
        status = status,
    )
}
