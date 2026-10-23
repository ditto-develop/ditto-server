package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoomType

/** 후보를 다시 만들어 후보 행이 없으면 score 는 null 이다. */
data class RatingWithMatchScore(
    val matchType: ChatRoomType,
    val rating: Int,
    val score: Double?,
)
