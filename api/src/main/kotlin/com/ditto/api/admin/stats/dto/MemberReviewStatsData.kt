package com.ditto.api.admin.stats.dto

import com.ditto.domain.review.repository.querydsl.MemberReviewCount
import com.ditto.domain.review.repository.querydsl.ReviewAnswerCount

data class MemberReviewStatsData(
    val memberReviewCounts: List<MemberReviewCount>,
    val answerCounts: List<ReviewAnswerCount>,
)
