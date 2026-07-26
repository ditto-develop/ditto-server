package com.ditto.api.memberreview.dto

import com.ditto.domain.chat.entity.ChatRoomType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 종료된 채팅방에서 평가를 열기 위해 필요한 값들. 채팅 종료 트랙이 만들어 넘기는 입력 계약이다.
 *
 * @property participantIds 종료 시점에 동결된 참여자 명단. 이후 멤버십이 바뀌어도 평가 대상은 이 값으로 고정된다
 * @property quizSetId `chat_room`에는 없고 원본 매칭까지 타고 들어가야 나오므로 종료 시점에 아는 쪽이 넘긴다
 * @property endedAt 평가 가능 시각이 된다
 */
data class EndedChatRoom(
    val chatRoomId: Long,
    val matchType: ChatRoomType,
    val matchId: Long,
    val quizSetId: Long,
    val weekStartedOn: LocalDate,
    val participantIds: List<Long>,
    val endedAt: LocalDateTime,
)
