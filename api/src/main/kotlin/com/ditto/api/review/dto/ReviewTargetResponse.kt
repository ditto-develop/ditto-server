package com.ditto.api.review.dto

import com.ditto.domain.member.entity.Member
import com.ditto.domain.review.entity.ReviewAnswer
import java.time.LocalDateTime

/**
 * 평가 대상 한 명 — 평가 화면에 필요한 프로필과 **내가 확정한 답변**, 그리고 상대의 재매칭 의사.
 * `answeredAt`이 `null`이면 아직 제출하지 않은 대상이다.
 * `counterpartWantsRematch`는 그룹 평가에서만 값이 있다 — `true` 면 상대가 나를 원한다고 냈다(받은 신청),
 * `false` 면 원하지 않는다고 냈다, `null` 이면 아직 안 냈거나 1:1 평가다.
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
    val counterpartWantsRematch: Boolean?,
) {
    companion object {
        fun of(
            answer: ReviewAnswer,
            member: Member?,
            counterpartWantsRematch: Boolean?,
        ): ReviewTargetResponse = ReviewTargetResponse(
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
            counterpartWantsRematch = counterpartWantsRematch,
        )
    }
}
