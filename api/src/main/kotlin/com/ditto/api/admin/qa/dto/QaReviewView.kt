package com.ditto.api.admin.qa.dto

import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.review.entity.MeetingStatus

/** 방에 열린 더미 한 명의 평가. 재매칭 의사는 그룹 평가에만 있다. */
class QaReview(
    val reviewId: Long,
    val author: QaMember,
    val canRematch: Boolean,
    val targets: List<QaReviewTarget>,
) {
    val pendingTargets: List<QaReviewTarget> = targets.filterNot { it.isAnswered }
}

/** 평가 대상 한 명. 상대의 의사와 쌍 상태는 재매칭 흐름을 확인하려고 함께 보여 준다. */
class QaReviewTarget(
    val member: QaMember,
    val meetingStatus: MeetingStatus?,
    val rating: Int?,
    val comment: String?,
    val isAnswered: Boolean,
    val authorWantsRematch: Boolean?,
    val counterpartWantsRematch: Boolean?,
    val rematchStatus: RematchStatus?,
)
