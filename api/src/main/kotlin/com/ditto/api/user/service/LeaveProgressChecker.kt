package com.ditto.api.user.service

import com.ditto.api.match.GroupResponseDeadline
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.system.OperationWeek
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * "진행 중인 매칭이나 채팅이 있으면 탈퇴가 제한됩니다"(피그마 6.2.4)에 걸리는지 본다. 진행 중은 넷이다.
 *
 * - 매칭 `PENDING`·`ACCEPTED` — 수락 대기 중인 요청도 상대가 기다리는 상태다.
 * - 끝나지 않은 방(`SCHEDULED`·`ACTIVE`) — `SCHEDULED`(개방 예정, 재매칭 방)도 상대가 곧 열릴 방을 기다린다.
 * - 성사됐는데 방이 아직 없는 재매칭 — 방은 스케줄러가 만들어 성사와 예약 사이에 한 주기(현재 1분)가
 *   빈다. 그 사이 탈퇴하면 위 방 조건을 빠져나가고, 뒤이은 예약이 탈퇴자와의 방을 만든다.
 * - 응답 마감 전, 성사 전 그룹에 수락해 둔 상태. 다른 사람이 수락하면 언제든 성사되고, 성사는 수락한
 *   사람 전원으로 방을 만든다. 마감이 지나 미성사로 끝난 그룹은 더 성사되지 않으므로 막지 않는다.
 *
 * 미성사(`WAITING`) 쌍은 막지 않고 탈퇴 시점에 취소한다([LeftMemberRematchCanceller]).
 */
@Component
class LeaveProgressChecker(
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val rematchRepository: RematchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    fun hasInProgress(memberId: Long, now: LocalDateTime): Boolean =
        personalMatchRepository.existsByMemberIdAndStatusIn(memberId, ONGOING_MATCH_STATUSES) ||
            chatRoomRepository.existsUnendedRoomOfMember(memberId) ||
            rematchRepository.existsMatchedWithoutChatRoomOfMember(memberId) ||
            isWaitingForGroupFormation(memberId, now)

    private fun isWaitingForGroupFormation(memberId: Long, now: LocalDateTime): Boolean {
        val thisWeek = OperationWeek.containing(now.toLocalDate())
        if (GroupResponseDeadline.hasPassed(thisWeek, now)) {
            return false
        }
        return groupMatchMemberRepository.existsAcceptedInUnformedGroupOfWeek(memberId, thisWeek.startedOn)
    }

    companion object {
        private val ONGOING_MATCH_STATUSES = setOf(
            PersonalMatchStatus.PENDING,
            PersonalMatchStatus.ACCEPTED,
        )
    }
}
