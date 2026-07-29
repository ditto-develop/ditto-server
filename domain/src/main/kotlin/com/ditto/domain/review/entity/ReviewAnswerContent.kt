package com.ditto.domain.review.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 대상 한 명에 대한 답변 내용. **[of]로만 만든다** — 코멘트 정규화와 값 검증이 거기서 일어난다.
 *
 * 세 값을 묶어 두는 이유는 재제출 판정 때문이다. 확정된 답변과 새 요청이 같은지 비교하려면
 * 양쪽이 같은 정규화를 거쳐야 하는데, 값이 흩어져 있으면 비교하는 쪽마다 정규화를 다시 구현하게 된다.
 */
data class ReviewAnswerContent(
    val meetingStatus: MeetingStatus,
    val rating: Int,
    val comment: String?,
) {
    init {
        if (rating !in RATING_MIN..RATING_MAX) {
            throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "별점은 $RATING_MIN~$RATING_MAX 사이여야 합니다.")
        }
        if (comment != null && comment.length > COMMENT_MAX_LENGTH) {
            throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "코멘트는 최대 ${COMMENT_MAX_LENGTH}자입니다.")
        }
    }

    companion object {
        const val COMMENT_MAX_LENGTH = 50
        const val RATING_MIN = 1
        const val RATING_MAX = 5

        fun of(meetingStatus: MeetingStatus, rating: Int, comment: String?): ReviewAnswerContent =
            ReviewAnswerContent(meetingStatus, rating, normalizedComment(comment))

        /** 공백만 입력한 코멘트는 미입력과 같으므로 `null`로 정규화한다. */
        private fun normalizedComment(comment: String?): String? = comment?.trim()?.ifEmpty { null }
    }
}
