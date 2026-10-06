package com.ditto.api.admin.qa.dto

import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.review.entity.MeetingStatus
import java.time.LocalDateTime

/** 방에 열린 더미 한 명의 평가. 재매칭 의사는 그룹 평가에만 있다. */
class QaReview(
    val reviewId: Long,
    val author: QaMember,
    val canRematch: Boolean,
    val targets: List<QaReviewTarget>,
) {
    val pendingTargets: List<QaReviewTarget> = targets.filterNot { it.isAnswered }

    val hasPendingTargets: Boolean = pendingTargets.isNotEmpty()

    val answeredCount: Int = targets.size - pendingTargets.size
}

class QaReviewTarget(
    val member: QaMember,
    val isDummy: Boolean,
    val isAnswered: Boolean,
    meetingStatus: MeetingStatus?,
    rating: Int?,
    comment: String?,
    val rematch: QaRematchPair?,
) {
    val answerSummary: String? = meetingStatus?.let { status ->
        listOfNotNull(status.description, "${rating}점", comment).joinToString(" · ")
    }
}

/** 평가 작성자 쪽에서 본 재매칭 쌍. 탈퇴로 취소된 쌍은 의사를 저장하지 않으므로 의사 대신 취소를 보여 준다. */
class QaRematchPair(
    val rematchId: Long,
    val status: RematchStatus,
    val isCancelledByMemberLeave: Boolean,
    val authorWants: Boolean?,
    val counterpartWants: Boolean?,
    val room: QaRematchRoom?,
) {
    val isMatched: Boolean = status == RematchStatus.MATCHED
}

class QaRematchRoom(
    val roomId: Long,
    val opensAt: LocalDateTime,
)
