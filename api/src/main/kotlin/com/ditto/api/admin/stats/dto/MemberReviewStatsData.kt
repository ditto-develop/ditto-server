package com.ditto.api.admin.stats.dto

import com.ditto.domain.review.repository.querydsl.MemberReviewCount
import com.ditto.domain.review.repository.querydsl.MutualRatingCount
import com.ditto.domain.review.repository.querydsl.RatingWithMatchScore
import com.ditto.domain.review.repository.querydsl.ReviewAnswerCount

data class MemberReviewStatsData(
    val memberReviewCounts: List<MemberReviewCount>,
    val reviewAnswerCounts: List<ReviewAnswerCount>,
    val mutualRatingCounts: List<MutualRatingCount>,
    val ratingsWithMatchScore: List<RatingWithMatchScore>,
)
