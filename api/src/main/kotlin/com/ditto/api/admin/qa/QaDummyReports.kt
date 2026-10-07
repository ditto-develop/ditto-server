package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaReportRow
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.admin.qa.dto.QaReportTarget
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
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
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun composeSection(realMembersInRequestsAndGroups: List<QaMember>): QaReportSection {
        val dummyIds = qaDummies.findIds().sorted()
        if (dummyIds.isEmpty()) return QaReportSection.EMPTY

        val reports = memberReportRepository.findByReporterIdInOrderByIdDesc(
            dummyIds,
            Limit.of(QaReportSection.RECENT_REPORT_LIMIT),
        )
        val realMemberIds = collectRealMemberIds(realMembersInRequestsAndGroups, reports, dummyIds.toSet())
        val members = qaMemberLabels.load(dummyIds + realMemberIds)
        val dummiesAvailableFirst = dummyIds.map(members::of).sortedBy { it.isUnavailable }

        return QaReportSection(
            dummies = dummiesAvailableFirst,
            targets = composeTargets(realMemberIds.map(members::of), dummiesAvailableFirst),
            reports = composeRows(reports, members),
        )
    }

    /**
     * 테스트 계정이 방을 나갔거나 아직 방이 없어도 고를 수 있게 넓게 모은다.
     * 더미와 같은 방에 있던 회원(최근 방 먼저), 신청·초대에 나온 회원, 더미가 이미 신고한 회원 순이다.
     */
    private fun collectRealMemberIds(
        realMembersInRequestsAndGroups: List<QaMember>,
        reports: List<MemberReport>,
        dummyIds: Set<Long>,
    ): List<Long> {
        val candidateIds = findMemberIdsSharingRoomsWith(dummyIds) +
            realMembersInRequestsAndGroups.map { it.id } +
            reports.map { it.reportedMemberId }
        return candidateIds.distinct().filterNot { it in dummyIds }
    }

    private fun findMemberIdsSharingRoomsWith(dummyIds: Set<Long>): List<Long> {
        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptyList()
        return chatRoomMemberRepository.findByRoomIdIn(roomIds).sortedByDescending { it.roomId }.map { it.memberId }
    }

    /** 화면은 첫 더미를 기본 신고자로 고른다. 실회원이 없으면 첫 대상이 자기 자신이 되니 기본 신고자를 맨 뒤로 보낸다. */
    private fun composeTargets(realMembers: List<QaMember>, dummies: List<QaMember>): List<QaReportTarget> {
        val dummiesWithDefaultReporterLast = dummies.drop(1) + dummies.take(1)
        return realMembers.map { QaReportTarget(it, isDummy = false) } +
            dummiesWithDefaultReporterLast.map { QaReportTarget(it, isDummy = true) }
    }

    private fun composeRows(reports: List<MemberReport>, members: QaMembers): List<QaReportRow> {
        val now = serverTimeProvider.now()
        val sanctionByReportId = sanctionRepository.findByMemberReportIdIn(reports.map { it.id })
            .associateBy { it.memberReportId }
        return reports.map { report ->
            QaReportRow(
                reportId = report.id,
                reporter = members.of(report.reporterId),
                reportedMember = members.of(report.reportedMemberId),
                reasonDescriptions = report.reasons.sorted().map { it.description },
                reportStatus = report.status,
                sanctionSummaryText = sanctionByReportId[report.id]?.let { summarizeSanction(it, now) },
            )
        }
    }

    /**
     * 신고 상태는 처리 결과만 말하니, 제재가 지금 어떤지를 함께 보여 준다.
     * 만료는 배치·로그인 때 반영돼 기간이 지나도 ACTIVE 로 남을 수 있다. 시작 전인 제재는 다음 주부터 걸리는 경고뿐이다.
     */
    private fun summarizeSanction(sanction: Sanction, now: LocalDateTime): String {
        val state = when {
            sanction.status != SanctionStatus.ACTIVE -> sanction.status.description
            !sanction.isEffectiveAt(now) -> "기간 지남"
            sanction.level == SanctionLevel.WARNING && sanction.startsAt > now ->
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
