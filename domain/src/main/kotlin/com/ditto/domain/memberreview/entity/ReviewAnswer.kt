package com.ditto.domain.memberreview.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

/**
 * 평가 대상 회원 한 명에 대한 응답. 진행 단위([MemberReview]) 생성 시 빈 행으로 만들어지고 이후 답변이 채워진다.
 *
 * 응답 여부는 [answeredAt] 하나로 표현한다 — 대상별 제출은 최종 확정이라 상태가 미응답/응답완료 둘뿐이고,
 * 별도 상태 컬럼을 두면 같은 사실이 두 곳에 저장돼 어긋날 수 있다.
 */
@Entity
@Table(
    name = "review_answer",
    uniqueConstraints = [
        UniqueConstraint(
            name = "review_answer_uk_1",
            columnNames = ["member_review_id", "reviewed_member_id"],
        ),
    ],
    indexes = [
        Index(name = "review_answer_index_1", columnList = "reviewed_member_id, answered_at"),
    ],
)
class ReviewAnswer private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("리뷰 진행 단위 ID")
    @Column(name = "member_review_id", nullable = false)
    val memberReviewId: Long,

    @Comment("평가 대상 회원 ID")
    @Column(name = "reviewed_member_id", nullable = false)
    val reviewedMemberId: Long,
) : BaseEntity() {

    @Comment("오프라인 만남 성사 여부 (미응답이면 NULL)")
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_status", length = 30)
    var meetingStatus: MeetingStatus? = null
        protected set

    @Comment("별점 1~5 (미응답이면 NULL)")
    @Column
    var rating: Int? = null
        protected set

    @Comment("한줄 코멘트 (선택)")
    @Column(length = COMMENT_MAX_LENGTH)
    var comment: String? = null
        protected set

    @Comment("최종 제출 시각. NULL 이면 미응답")
    @Column(name = "answered_at")
    var answeredAt: LocalDateTime? = null
        protected set

    val isAnswered: Boolean
        get() = answeredAt != null

    /**
     * 이 대상에 대한 답변을 확정한다. 대상별 제출은 최종이라 한 번만 성공하며, 이후 재제출은 거부한다.
     * 같은 본문 재전송의 멱등 처리는 서비스가 확정 여부를 먼저 확인해 수행한다.
     */
    fun answer(
        meetingStatus: MeetingStatus,
        rating: Int,
        comment: String?,
        answeredAt: LocalDateTime,
    ) {
        if (isAnswered) {
            throw WarnException(ErrorCode.REVIEW_ALREADY_ANSWERED)
        }
        // 검증을 모두 마친 뒤 대입한다 — 중간에 예외가 나면 일부 필드만 바뀐 채로 남는다.
        val validatedRating = validRating(rating)
        val validatedComment = normalizedComment(comment)

        this.meetingStatus = meetingStatus
        this.rating = validatedRating
        this.comment = validatedComment
        this.answeredAt = answeredAt
    }

    companion object {
        const val COMMENT_MAX_LENGTH = 50
        const val RATING_MIN = 1
        const val RATING_MAX = 5

        fun pending(
            memberReviewId: Long,
            reviewedMemberId: Long,
        ): ReviewAnswer = ReviewAnswer(
            memberReviewId = memberReviewId,
            reviewedMemberId = reviewedMemberId,
        )

        private fun validRating(rating: Int): Int {
            if (rating !in RATING_MIN..RATING_MAX) {
                throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "별점은 $RATING_MIN~$RATING_MAX 사이여야 합니다.")
            }
            return rating
        }

        /** 공백만 입력한 코멘트는 미입력과 같으므로 `null`로 정규화한다. */
        private fun normalizedComment(comment: String?): String? {
            val trimmed = comment?.trim()
            if (trimmed.isNullOrEmpty()) return null
            if (trimmed.length > COMMENT_MAX_LENGTH) {
                throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "코멘트는 최대 ${COMMENT_MAX_LENGTH}자입니다.")
            }
            return trimmed
        }
    }
}
