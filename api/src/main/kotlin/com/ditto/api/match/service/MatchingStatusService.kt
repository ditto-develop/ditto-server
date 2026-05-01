package com.ditto.api.match.service

import com.ditto.api.match.dto.GroupMatchStatus
import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.api.match.dto.PersonalMatchSummary
import com.ditto.domain.match.entity.PersonalMatch
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
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
) {

    fun getMatchingStatus(memberId: Long, quizSetId: Long): MatchingStatusResponse {
        val personalMatch = resolvePersonalMatch(memberId, quizSetId)
        val (groupMatchStatus, groupMatchRoomId) = resolveGroupMatchStatus(memberId, quizSetId)

        return MatchingStatusResponse(
            quizSetId = quizSetId,
            personalMatch = personalMatch?.let { PersonalMatchSummary.from(it) },
            groupMatchStatus = groupMatchStatus,
            groupMatchRoomId = groupMatchRoomId,
        )
    }

    /** ACCEPTED 매칭 우선, 없으면 PENDING 반환 */
    private fun resolvePersonalMatch(memberId: Long, quizSetId: Long): PersonalMatch? {
        return personalMatchRepository.findMatchByQuizSetIdAndStatusAndMemberId(
            quizSetId, PersonalMatchStatus.ACCEPTED, memberId,
        ) ?: personalMatchRepository.findMatchByQuizSetIdAndStatusAndMemberId(
            quizSetId, PersonalMatchStatus.PENDING, memberId,
        )
    }

    /** 그룹 매칭 상태: DECLINED > JOINED > NONE 순으로 판단 */
    private fun resolveGroupMatchStatus(memberId: Long, quizSetId: Long): Pair<GroupMatchStatus, Long?> {
        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            return Pair(GroupMatchStatus.DECLINED, null)
        }

        val memberships = groupMatchMemberRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (memberships.isNotEmpty()) {
            // 여러 방에 참여했을 경우 가장 최근 방 반환
            val latestMembership = memberships.maxByOrNull { it.createdAt }!!
            return Pair(GroupMatchStatus.JOINED, latestMembership.roomId)
        }

        return Pair(GroupMatchStatus.NONE, null)
    }
}
