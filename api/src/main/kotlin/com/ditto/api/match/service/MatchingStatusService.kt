package com.ditto.api.match.service

import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
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
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
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

    /** 그룹 매칭 상태: 거절 > (활성 방=joined / 비활성 방=pending) > 미참여 */
    private fun groupFlags(memberId: Long, quizSetId: Long): GroupFlags {
        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            return GroupFlags(declined = true, joined = false, pending = false)
        }

        val latestMembership = groupMatchMemberRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
            .maxByOrNull { it.createdAt }
            ?: return GroupFlags(declined = false, joined = false, pending = false)

        val room = groupMatchRepository.findById(latestMembership.roomId).orElseThrow {
            ErrorException(ErrorCode.INTERNAL_ERROR, "참여 기록의 그룹 방이 없습니다: roomId=${latestMembership.roomId}")
        }
        return GroupFlags(declined = false, joined = room.isActive, pending = !room.isActive)
    }

    private data class GroupFlags(
        val declined: Boolean,
        val joined: Boolean,
        val pending: Boolean,
    )
}
