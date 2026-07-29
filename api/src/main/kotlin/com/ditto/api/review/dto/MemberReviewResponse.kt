package com.ditto.api.review.dto

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.member.entity.Member
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewAnswer
import com.ditto.domain.review.entity.ReviewProgressStatus
import java.time.LocalDateTime

/**
 * 내 미완료 평가 하나. `matchType`·`matchId`는 엔티티와 같은 이름을 쓴다 — 배경은
 * `docs/domains/review.md` 명명 절 참고.
 */
data class MemberReviewResponse(
    val reviewId: Long,
    val matchType: ChatRoomType,
    val matchId: Long,
    val chatRoomId: Long,
    val availableAt: LocalDateTime,
    val status: ReviewProgressStatus,
    val answeredTargetCount: Int,
    val totalTargetCount: Int,
    val targets: List<ReviewTargetResponse>,
) {
    companion object {
        fun of(
            review: MemberReview,
            answers: List<ReviewAnswer>,
            membersById: Map<Long, Member>,
        ): MemberReviewResponse = MemberReviewResponse(
            reviewId = review.id,
            matchType = review.matchType,
            matchId = review.matchId,
            chatRoomId = review.chatRoomId,
            availableAt = review.availableAt,
            status = review.status,
            answeredTargetCount = answers.count { it.isAnswered },
            totalTargetCount = answers.size,
            targets = answers.map { ReviewTargetResponse.of(it, membersById[it.reviewedMemberId]) },
        )
    }
}
