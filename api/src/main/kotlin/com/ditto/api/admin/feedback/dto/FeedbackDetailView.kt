package com.ditto.api.admin.feedback.dto

import com.ditto.domain.member.entity.MemberStatus
import java.time.LocalDateTime

data class FeedbackDetailView(
    val id: Long,
    val typeDescription: String,
    val statusDescription: String,
    val awaitingCompletion: Boolean,
    val content: String,
    val appVersion: String?,
    val os: String?,
    val deviceModel: String?,
    val memberId: Long,
    val memberNickname: String,
    /** 회원 행이 지워졌으면 null. */
    val memberStatus: MemberStatus?,
    val createdAt: LocalDateTime,
    val imageUrls: List<String>,
    val completedByName: String?,
    val completedAt: LocalDateTime?,
) {
    val hasDeviceInfo: Boolean = appVersion != null || os != null || deviceModel != null
}
