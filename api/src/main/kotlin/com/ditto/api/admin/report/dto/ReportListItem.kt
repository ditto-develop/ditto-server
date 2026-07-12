package com.ditto.api.admin.report.dto

import java.time.LocalDateTime

/** 신고 검토 목록의 한 행. */
data class ReportListItem(
    val id: Long,
    val reasonDescription: String,
    val recommendsSuspension: Boolean,
    val reporterNickname: String,
    val reportedNickname: String,
    val statusDescription: String,
    val received: Boolean,
    val createdAt: LocalDateTime,
    val elapsedText: String,
    val overdue: Boolean,
)
