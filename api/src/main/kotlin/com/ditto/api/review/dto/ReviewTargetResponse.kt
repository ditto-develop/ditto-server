package com.ditto.api.review.dto

import com.ditto.domain.member.entity.Member
import com.ditto.domain.review.entity.ReviewAnswer
import java.time.LocalDateTime

/**
 * 평가 대상 한 명 — 평가 화면에 필요한 프로필과 **내가 확정한 답변**.
 * `answeredAt`이 `null`이면 아직 제출하지 않은 대상이다.
 *
 * 탈퇴 등으로 회원이 사라져도 대상 행은 남으므로 프로필 필드는 모두 nullable이다.
 * `location`은 FE와 공유하는 `code`로, `gender`·`meetingStatus`는 enum 이름으로 반환한다.
 */
data class ReviewTargetResponse(
    val memberId: Long,
    val nickname: String?,
    val gender: String?,
    val age: Int?,
    val location: String?,
    val profileImageUrl: String?,
    val meetingStatus: String?,
    val rating: Int?,
    val comment: String?,
    val answeredAt: LocalDateTime?,
) {
    companion object {
        fun of(answer: ReviewAnswer, member: Member?): ReviewTargetResponse = ReviewTargetResponse(
            memberId = answer.reviewedMemberId,
            nickname = member?.nickname,
            gender = member?.gender?.name,
            age = member?.age,
            location = member?.location?.code,
            profileImageUrl = member?.caricature,
            meetingStatus = answer.meetingStatus?.name,
            rating = answer.rating,
            comment = answer.comment,
            answeredAt = answer.answeredAt,
        )
    }
}
