package com.ditto.api.admin.qa

import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.domain.review.entity.MeetingStatus

/** 더미 남은 평가를 한꺼번에 낼 때의 답. 재매칭 의사는 그룹 평가에만 싣는다. */
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

/** 일괄 제출의 재매칭 의사. 더미끼리 모두 원하면 더미끼리 재매칭 방이 여럿 생겨 실회원에게만 원하는 것을 기본으로 둔다. */
enum class QaBulkRematchChoice(val label: String) {
    REAL_MEMBERS_ONLY("실회원에게만 재매칭 원함"),
    EVERYONE("모두에게 재매칭 원함"),
    NOBODY("모두 재매칭 안 함"),
    ;

    fun wantsToward(targetIsDummy: Boolean): Boolean =
        when (this) {
            REAL_MEMBERS_ONLY -> !targetIsDummy
            EVERYONE -> true
            NOBODY -> false
        }
}
