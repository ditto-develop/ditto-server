package com.ditto.api.user.service

import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Component

/**
 * "진행 중인 매칭이나 채팅이 있으면 탈퇴가 제한됩니다"(피그마 6.2.4)를 판정한다.
 *
 * 매칭은 PENDING·ACCEPTED를 진행 중으로 본다 — 수락 대기 중인 요청도 상대가 기다리는 상태다.
 *
 * 채팅은 **아직 끝나지 않은 방(SCHEDULED·ACTIVE)** 이 있으면 진행 중으로 본다.
 * SCHEDULED(개방 예정, 재매칭 방)도 포함한다 — 상대가 곧 열릴 방을 기다리는 상태다.
 *
 * 미성사(WAITING) 재매칭 쌍은 진행 중으로 보지 않는다 — 탈퇴 시점에 취소한다
 * ([LeftMemberRematchCanceller]).
 */
@Component
class LeaveProgressChecker(
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatRoomRepository: ChatRoomRepository,
) {

    fun hasInProgress(memberId: Long): Boolean =
        personalMatchRepository.existsByMemberIdAndStatusIn(memberId, ONGOING_MATCH_STATUSES) ||
            chatRoomRepository.existsUnendedRoomOfMember(memberId)

    companion object {
        private val ONGOING_MATCH_STATUSES = setOf(
            PersonalMatchStatus.PENDING,
            PersonalMatchStatus.ACCEPTED,
        )
    }
}
