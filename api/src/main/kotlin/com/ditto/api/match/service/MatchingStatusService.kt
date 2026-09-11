package com.ditto.api.match.service

import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MatchingStatusService(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    fun getMatchingStatus(memberId: Long, quizSetId: Long): MatchingStatusResponse {
        val personalMatches = personalMatchService.getPersonalMatches(memberId, quizSetId)
        val acceptedMatchUserId = findAcceptedMatchUserId(memberId, quizSetId)
        val group = groupFlags(memberId, quizSetId)

        return MatchingStatusResponse(
            quizSetId = quizSetId,
            sentRequests = personalMatches.sent,
            receivedRequests = personalMatches.received,
            hasAcceptedMatch = acceptedMatchUserId != null,
            acceptedMatchUserId = acceptedMatchUserId,
            groupDeclined = group.declined,
            groupJoined = group.joined,
            groupJoinPending = group.pending,
        )
    }

    /** ACCEPTED 1:1 매칭이 있으면 상대 회원 ID, 없으면 null */
    private fun findAcceptedMatchUserId(memberId: Long, quizSetId: Long): Long? {
        val accepted = personalMatchRepository.findMatchByQuizSetIdAndStatusAndMemberId(
            quizSetId, PersonalMatchStatus.ACCEPTED, memberId,
        ) ?: return null
        return accepted.counterpartOf(memberId)
    }

    /**
     * 그룹 매칭 상태. 초대 응답이 그룹별로 갈리므로 내 초대들을 모아 판단한다.
     * - joined: 수락했고 그 그룹이 성사됨(채팅방이 열림)
     * - pending: 수락했지만 아직 인원이 모자람
     * - declined: 받은 초대를 모두 거절함
     */
    private fun groupFlags(memberId: Long, quizSetId: Long): GroupFlags {
        val invitations = groupMatchMemberRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (invitations.isEmpty()) {
            return GroupFlags(declined = false, joined = false, pending = false)
        }

        val accepted = invitations.firstOrNull { it.status == InvitationStatus.ACCEPTED }
            ?: return GroupFlags(
                declined = invitations.all { it.status == InvitationStatus.DECLINED },
                joined = false,
                pending = false,
            )

        val room = groupMatchRepository.findById(accepted.roomId).orElseThrow {
            ErrorException(ErrorCode.INTERNAL_ERROR, "참여 기록의 그룹 방이 없습니다: roomId=${accepted.roomId}")
        }
        return GroupFlags(declined = false, joined = room.isActive, pending = !room.isActive)
    }

    private data class GroupFlags(
        val declined: Boolean,
        val joined: Boolean,
        val pending: Boolean,
    )
}
