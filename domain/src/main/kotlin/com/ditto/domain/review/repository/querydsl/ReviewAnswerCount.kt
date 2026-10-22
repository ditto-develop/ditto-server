package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.review.entity.MeetingStatus

data class ReviewAnswerCount(
    val matchType: ChatRoomType,
    val meetingStatus: MeetingStatus,
    val rating: Int,
    val count: Long,
)
