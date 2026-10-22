package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.review.entity.ReviewProgressStatus

data class MemberReviewCount(
    val matchType: ChatRoomType,
    val status: ReviewProgressStatus,
    val count: Long,
)
