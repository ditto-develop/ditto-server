package com.ditto.api.review.dto

import com.ditto.domain.chat.entity.ChatRoomType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 채팅방에서 평가를 열기 위해 필요한 값들. 보통 끝난 방이고, 열린 방에서 나간 사람 한 명분일 때도 있다.
 *
 * @property participantIds 종료 시점의 참여자 명단. 중간에 나간 사람도 포함한다
 * @property quizSetId `chat_room`에는 없고 원본 매칭까지 타고 들어가야 나오므로 종료 시점에 아는 쪽이 넘긴다
 * @property reviewAvailableAt 보통 방이 끝난 시각이고, 나간 사람 한 명분이면 나간 시각이다
 */
data class EndedChatRoom(
    val chatRoomId: Long,
    val matchType: ChatRoomType,
    val matchId: Long,
    val quizSetId: Long,
    val weekStartedOn: LocalDate,
    val participantIds: List<Long>,
    val reviewAvailableAt: LocalDateTime,
) {
    /** 중복을 제거한 참여자 명단. 같은 회원이 두 번 실려 와도 평가는 한 번만 열린다. */
    val reviewerIds: List<Long>
        get() = participantIds.distinct()

    /** [reviewerId]가 평가할 대상들 — 자기 자신은 평가하지 않는다. */
    fun targetIdsFor(reviewerId: Long): List<Long> = reviewerIds.filter { it != reviewerId }
}
