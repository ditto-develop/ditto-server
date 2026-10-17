package com.ditto.api.admin.feedback.dto

import java.time.LocalDateTime

data class FeedbackListItem(
    val id: Long,
    val typeDescription: String,
    val contentPreview: String,
    val memberNickname: String,
    val imageCount: Int,
    val createdAt: LocalDateTime,
    val completedByName: String?,
    val completedAt: LocalDateTime?,
)
