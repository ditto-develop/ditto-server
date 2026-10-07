package com.ditto.api.admin.report.dto

import com.ditto.api.admin.dummy.DummyMarker
import java.time.LocalDateTime

/** 신고 검토 목록의 한 행. */
data class ReportListItem(
    val id: Long,
    /** 선택한 사유 전부, 심각한 순. */
    val reasonDescriptions: List<String>,
    val isSevere: Boolean,
    val reporterNickname: String,
    val reportedNickname: String,
    val statusDescription: String,
    val received: Boolean,
    val createdAt: LocalDateTime,
    val elapsedText: String,
    val overdue: Boolean,
) {
    val isReportedByQaDummy: Boolean = DummyMarker.isDummy(reporterNickname)
}
