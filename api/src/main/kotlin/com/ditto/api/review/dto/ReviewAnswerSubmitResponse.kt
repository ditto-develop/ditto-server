package com.ditto.api.review.dto

import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.entity.ReviewProgressStatus
import java.time.LocalDateTime

/**
 * 제출 후의 진행 상태. 마지막 대상을 제출하면 `status`가 `COMPLETED`가 되므로 별도 완료 API는 없다.
 *
 * @property rematch **이번 제출로 상호 성사된 경우에만** 채운다. 상대의 선택 자체는 목록 조회의
 *   `targets[].counterpartWantsRematch` 로 본다
 */
data class ReviewAnswerSubmitResponse(
    val reviewId: Long,
    val status: ReviewProgressStatus,
    val answeredTargetCount: Int,
    val totalTargetCount: Int,
    val completedAt: LocalDateTime?,
    val rematch: RematchResultResponse?,
) {
    companion object {
        fun of(
            review: MemberReview,
            answers: List<ReviewAnswer>,
            rematch: RematchResultResponse?,
        ): ReviewAnswerSubmitResponse = ReviewAnswerSubmitResponse(
            reviewId = review.id,
            status = review.status,
            answeredTargetCount = answers.count { it.isAnswered },
            totalTargetCount = answers.size,
            completedAt = review.completedAt,
            rematch = rematch,
        )
    }
}
