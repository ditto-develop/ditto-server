package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType

data class MutualRatingCount(
    val matchType: ChatRoomType,
    val rating: Int,
    val counterpartRating: Int,
    val count: Long,
)
