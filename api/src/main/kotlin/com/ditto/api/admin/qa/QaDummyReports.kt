package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaReportRow
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.admin.qa.dto.QaReportTarget
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 콘솔의 더미로 신고하기 카드. 신고는 앱 API 로 내고, 여기서는 고를 대상과 더미가 낸 신고를 모은다. */
@Component
@Transactional(readOnly = true)
class QaDummyReports(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val memberRepository: MemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {
    /** 화면은 첫 더미를 기본 신고자로 고른다. id 순으로 둬 기본값이 매번 같게 한다. */
    fun composeSection(console: QaConsoleView): QaReportSection {
        val dummyIds = qaDummies.findIds().sorted()
        if (dummyIds.isEmpty()) return QaReportSection.EMPTY

        val reports = memberReportRepository.findByReporterIdInOrderByIdDesc(
            dummyIds,
            Limit.of(QaReportSection.RECENT_REPORT_LIMIT),
        )
        val realMemberIds = collectRealMemberIds(console, reports, dummyIds.toSet())
        val members = qaMemberLabels.load(dummyIds + realMemberIds + reports.map { it.reportedMemberId })
        val dummies = dummyIds.map(members::of).sortedBy { it.isRestricted }

        return QaReportSection(
            dummies = dummies,
            targets = composeTargets(realMemberIds.map(members::of), dummies),
            reports = composeRows(reports, members),
        )
    }

    /**
     * 테스트 계정이 방을 나갔거나 아직 방이 없어도 고를 수 있게 넓게 모은다.
     * 더미와 같은 방에 있던 회원(최근 방 먼저), 신청·초대에 나온 회원, 더미가 이미 신고한 회원 순이다.
     */
    private fun collectRealMemberIds(
        console: QaConsoleView,
        reports: List<MemberReport>,
        dummyIds: Set<Long>,
    ): List<Long> =
        (
            findMemberIdsSharingRoomsWith(dummyIds) +
                console.realMembersInRequestsAndGroups.map { it.id } +
                reports.map { it.reportedMemberId }
            ).distinct().filterNot { it in dummyIds }

    private fun findMemberIdsSharingRoomsWith(dummyIds: Set<Long>): List<Long> {
        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptyList()
        return chatRoomMemberRepository.findByRoomIdIn(roomIds).sortedByDescending { it.roomId }.map { it.memberId }
    }

    /** 실회원이 없으면 첫 대상이 기본 신고자와 같아 자기 신고로 거부되니, 기본 신고자를 맨 뒤로 보낸다. */
    private fun composeTargets(realMembers: List<QaMember>, dummies: List<QaMember>): List<QaReportTarget> {
        val dummiesWithDefaultReporterLast = dummies.drop(1) + dummies.take(1)
        return realMembers.map { QaReportTarget(it, isDummy = false) } +
            dummiesWithDefaultReporterLast.map { QaReportTarget(it, isDummy = true) }
    }

    private fun composeRows(reports: List<MemberReport>, members: QaMembers): List<QaReportRow> {
        val now = serverTimeProvider.now()
        val statusByMemberId = memberRepository.findAllById(reports.map { it.reportedMemberId }.distinct())
            .associate { it.id to it.status }
        val sanctionByReportId = sanctionRepository.findByMemberReportIdIn(reports.map { it.id })
            .associateBy { it.memberReportId }
        return reports.map { report ->
            report.toRow(members, statusByMemberId[report.reportedMemberId], sanctionByReportId[report.id], now)
        }
    }

    private fun MemberReport.toRow(
        members: QaMembers,
        reportedMemberStatus: MemberStatus?,
        sanction: Sanction?,
        now: LocalDateTime,
    ) = QaReportRow(
        reportId = id,
        reporter = members.of(reporterId),
        reportedMember = members.of(reportedMemberId),
        reportedMemberStatus = reportedMemberStatus,
        reasonDescriptions = reasons.sorted().map { it.description },
        status = status,
        sanctionResult = sanction?.let { describeSanction(it, now) },
    )

    /** 신고 상태는 처리 결과만 말하니, 제재가 지금 어떤지(시작 전·적용 중·해제)를 함께 보여 준다. */
    private fun describeSanction(sanction: Sanction, now: LocalDateTime): String {
        val state = when {
            sanction.status == SanctionStatus.ACTIVE && sanction.startsAt > now ->
                "${SANCTION_START_FORMATTER.format(sanction.startsAt)}부터"
            else -> sanction.status.description
        }
        return "${sanction.level.description} · $state"
    }

    companion object {
        private val SANCTION_START_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MM/dd(E) HH:mm", Locale.KOREAN)
    }
}
