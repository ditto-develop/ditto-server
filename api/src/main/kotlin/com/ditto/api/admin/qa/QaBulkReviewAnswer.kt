package com.ditto.api.admin.qa

import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.domain.review.entity.MeetingStatus

/** 더미 남은 평가를 한꺼번에 낼 때의 답. 재매칭 의사는 화면에서 고른 값을 그룹 평가에만 싣는다. */
object QaBulkReviewAnswer {
    private val MEETING_STATUS = MeetingStatus.MET
    private const val RATING = 5

    val description: String = "${MEETING_STATUS.description} · ${RATING}점"

    fun request(canRematch: Boolean, wantsRematch: Boolean): ReviewAnswerSubmitRequest =
        ReviewAnswerSubmitRequest(
            meetingStatus = MEETING_STATUS,
            rating = RATING,
            wantsOneToOneRematch = wantsRematch.takeIf { canRematch },
        )
}
