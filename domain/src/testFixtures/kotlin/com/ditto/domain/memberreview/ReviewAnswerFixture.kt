package com.ditto.domain.memberreview

import com.ditto.domain.memberreview.entity.ReviewAnswer
import com.ditto.domain.withId

object ReviewAnswerFixture {

    fun pending(
        memberReviewId: Long = 1L,
        reviewedMemberId: Long = 2L,
        id: Long = 0L,
    ): ReviewAnswer = ReviewAnswer.pending(
        memberReviewId = memberReviewId,
        reviewedMemberId = reviewedMemberId,
    ).withId(id)
}
