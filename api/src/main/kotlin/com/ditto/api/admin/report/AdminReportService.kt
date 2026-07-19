package com.ditto.api.admin.report

import com.ditto.api.admin.report.dto.ReportDetailView
import com.ditto.api.admin.report.dto.ReportListItem
import com.ditto.api.admin.report.dto.ReportedSummary
import com.ditto.api.admin.report.dto.ReporterSummary
import com.ditto.api.admin.report.dto.ReviewSummary
import com.ditto.api.admin.report.dto.SanctionHistoryItem
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportImageRepository
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.infrastructure.storage.ObjectStorage
import java.time.Duration
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminReportService(
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    private val objectStorage: ObjectStorage,
) {

    @Transactional(readOnly = true)
    fun listReports(status: MemberReportStatus, now: LocalDateTime): List<ReportListItem> {
        val reports = memberReportRepository.findAllByStatusOrderByCreatedAtAsc(status)
        val nicknames = nicknamesOf(reports.flatMap { listOf(it.reporterId, it.reportedMemberId) })

        return reports.map { report ->
            val elapsed = Duration.between(report.createdAt, now)
            ReportListItem(
                id = report.id,
                reasonDescription = report.reason.description,
                isSevere = report.reason.isSevere,
                reporterNickname = nicknames.getValue(report.reporterId),
                reportedNickname = nicknames.getValue(report.reportedMemberId),
                statusDescription = report.status.description,
                received = report.status == MemberReportStatus.RECEIVED,
                createdAt = report.createdAt,
                elapsedText = formatElapsed(elapsed),
                overdue = report.status == MemberReportStatus.RECEIVED && elapsed > REVIEW_SLA,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getReportDetail(id: Long, now: LocalDateTime): ReportDetailView {
        val report = memberReportRepository.findById(id).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND)
        val nicknames = nicknamesOf(listOf(report.reporterId, report.reportedMemberId))
        val elapsed = Duration.between(report.createdAt, now)

        return ReportDetailView(
            id = report.id,
            reasonDescription = report.reason.description,
            isSevere = report.reason.isSevere,
            guideline = report.reason.guideline,
            sourceDescription = report.source.description,
            detail = report.detail,
            createdAt = report.createdAt,
            elapsedText = formatElapsed(elapsed),
            overdue = report.status == MemberReportStatus.RECEIVED && elapsed > REVIEW_SLA,
            received = report.status == MemberReportStatus.RECEIVED,
            reporter = reporterSummary(report, nicknames.getValue(report.reporterId)),
            reported = reportedSummary(report, nicknames.getValue(report.reportedMemberId)),
            imageUrls = imageViewUrls(report.id),
            review = reviewSummary(report),
        )
    }

    private fun reporterSummary(report: MemberReport, nickname: String) = ReporterSummary(
        memberId = report.reporterId,
        nickname = nickname,
        totalReportCount = memberReportRepository.countByReporterId(report.reporterId),
        abusiveRejectedCount = memberReportRepository.countByReporterIdAndStatus(
            report.reporterId,
            MemberReportStatus.REJECTED_ABUSIVE,
        ),
    )

    private fun reportedSummary(report: MemberReport, nickname: String): ReportedSummary {
        val sanctions = sanctionRepository.findAllByMemberIdOrderByIdDesc(report.reportedMemberId)
        return ReportedSummary(
            memberId = report.reportedMemberId,
            nickname = nickname,
            statusName = memberRepository.findById(report.reportedMemberId).getOrNull()?.status?.name ?: "탈퇴",
            recommendedStrike = sanctionRepository.countStrikes(report.reportedMemberId) + 1,
            sanctions = sanctions.map { sanction ->
                SanctionHistoryItem(
                    levelDescription = sanction.level.description,
                    originDescription = sanction.origin.description,
                    statusDescription = sanction.status.description,
                    startsAt = sanction.startsAt,
                    endsAt = sanction.endsAt,
                    creatorName = sanction.creatorName,
                    note = sanction.note,
                )
            },
        )
    }

    private fun imageViewUrls(reportId: Long): List<String> =
        memberReportImageRepository.findAllByMemberReportIdOrderByDisplayOrder(reportId)
            .map { objectStorage.issueViewUrl(it.objectKey) }

    private fun reviewSummary(report: MemberReport): ReviewSummary? {
        if (report.status == MemberReportStatus.RECEIVED) return null
        return ReviewSummary(
            statusDescription = report.status.description,
            reviewerName = report.reviewerName,
            reviewedAt = report.reviewedAt,
            note = report.reviewNote,
        )
    }

    private fun nicknamesOf(memberIds: List<Long>): Map<Long, String> {
        val found = memberRepository.findAllById(memberIds.distinct()).associate { it.id to it.nickname }
        return memberIds.associateWith { found[it] ?: "(탈퇴한 회원 #$it)" }
    }

    private fun formatElapsed(elapsed: Duration): String {
        val days = elapsed.toDays()
        val hours = elapsed.toHours() % 24
        val minutes = elapsed.toMinutes() % 60
        return when {
            days > 0 -> "${days}일 ${hours}시간 경과"
            hours > 0 -> "${hours}시간 ${minutes}분 경과"
            else -> "${minutes}분 경과"
        }
    }

    companion object {
        // 기획: 신고는 24시간 내 관리자 수동 검토 — 초과 시 목록에서 강조한다.
        private val REVIEW_SLA = Duration.ofHours(24)
    }
}
