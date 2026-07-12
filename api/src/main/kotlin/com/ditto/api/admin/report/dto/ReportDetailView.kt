package com.ditto.api.admin.report.dto

import java.time.LocalDateTime

/** 신고 검토 상세 — 한 화면에서 판단에 필요한 모든 정보를 담는다. */
data class ReportDetailView(
    val id: Long,
    val reasonDescription: String,
    val isSevere: Boolean,
    val guideline: String,
    val sourceDescription: String,
    val detail: String?,
    val createdAt: LocalDateTime,
    val elapsedText: String,
    val overdue: Boolean,
    val received: Boolean,
    val reporter: ReporterSummary,
    val reported: ReportedSummary,
    val imageUrls: List<String>,
    val review: ReviewSummary?,
)

/** 신고자 요약 — 허위 신고 이력 판단용. */
data class ReporterSummary(
    val memberId: Long,
    val nickname: String,
    val totalReportCount: Long,
    val abusiveRejectedCount: Long,
)

/** 피신고자 요약 — 제재 수위 판단용. */
data class ReportedSummary(
    val memberId: Long,
    val nickname: String,
    val statusName: String,
    val recommendedStrike: Long,
    val sanctions: List<SanctionHistoryItem>,
)

/** 제재 이력의 한 행. */
data class SanctionHistoryItem(
    val levelDescription: String,
    val originDescription: String,
    val statusDescription: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val creatorName: String,
    val note: String?,
)

/** 검토 완료 정보 (RECEIVED면 null). */
data class ReviewSummary(
    val statusDescription: String,
    val reviewerName: String?,
    val reviewedAt: LocalDateTime?,
    val note: String?,
)
