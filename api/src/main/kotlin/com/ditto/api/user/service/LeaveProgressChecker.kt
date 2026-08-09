package com.ditto.api.user.service

import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import org.springframework.stereotype.Component

/**
 * "진행 중인 매칭이나 채팅이 있으면 탈퇴가 제한됩니다"(피그마 6.2.4)를 판정한다. 진행 중은 셋이다.
 *
 * - 매칭 `PENDING`·`ACCEPTED` — 수락 대기 중인 요청도 상대가 기다리는 상태다.
 * - 끝나지 않은 방(`SCHEDULED`·`ACTIVE`) — `SCHEDULED`(개방 예정, 재매칭 방)도 상대가 곧 열릴 방을 기다린다.
 * - 성사됐는데 방이 아직 없는 재매칭 — 방은 스케줄러가 만들어 성사와 예약 사이에 한 주기(현재 1분)가
 *   빈다. 그 사이 탈퇴하면 위 방 조건을 빠져나가고, 뒤이은 예약이 탈퇴자와의 방을 만든다.
 *
 * 미성사(`WAITING`) 쌍은 막지 않고 탈퇴 시점에 취소한다([LeftMemberRematchCanceller]).
 */
@Component
class LeaveProgressChecker(
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val rematchRepository: RematchRepository,
) {

    fun hasInProgress(memberId: Long): Boolean =
        personalMatchRepository.existsByMemberIdAndStatusIn(memberId, ONGOING_MATCH_STATUSES) ||
            chatRoomRepository.existsUnendedRoomOfMember(memberId) ||
            rematchRepository.existsMatchedWithoutChatRoomOfMember(memberId)

    companion object {
        private val ONGOING_MATCH_STATUSES = setOf(
            PersonalMatchStatus.PENDING,
            PersonalMatchStatus.ACCEPTED,
        )
    }
}
