package com.ditto.api.chat.dto

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.review.entity.MemberReview
import com.ditto.domain.review.entity.ReviewProgressStatus

/**
 * 채팅방 목록에서 본 내 평가 상태. 평가 목록(`/member-reviews`)은 미완료만 주므로, "이미 끝냄"과 "아직 안 열림"이
 * 둘 다 "목록에 없음"으로 보였다 — 방 쪽에서 둘을 가른다.
 */
enum class ChatRoomReviewStatus {
    /** 평가가 없는 방 — 재매칭 방은 평가를 열지 않는다. */
    NOT_APPLICABLE,

    /** 아직 열리지 않았다 — 방이 끝나지 않았거나, 끝났지만 평가 생성이 아직이다(곧 복구 배치가 연다). */
    NOT_OPENED,
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    ;

    companion object {
        fun of(room: ChatRoom, myReview: MemberReview?): ChatRoomReviewStatus {
            if (room.sourceType !in MemberReview.REVIEWABLE_MATCH_TYPES) {
                return NOT_APPLICABLE
            }
            return when (myReview?.status) {
                null -> NOT_OPENED
                ReviewProgressStatus.NOT_STARTED -> NOT_STARTED
                ReviewProgressStatus.IN_PROGRESS -> IN_PROGRESS
                ReviewProgressStatus.COMPLETED -> COMPLETED
            }
        }
    }
}
