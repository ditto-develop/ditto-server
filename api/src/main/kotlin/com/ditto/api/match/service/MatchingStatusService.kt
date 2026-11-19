package com.ditto.api.match.service

import com.ditto.api.match.GroupMatchingStatus
import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MatchingStatusService(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchingStatusReader: GroupMatchingStatusReader,
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

    // 시크릿 매칭 1:1 응시 판단과 같은 상태를 써서 그룹 화면과 1:1 제출이 어긋나지 않게 한다.
    private fun groupFlags(memberId: Long, quizSetId: Long): GroupFlags {
        val status = groupMatchingStatusReader.read(memberId, quizSetId)
        return GroupFlags(
            declined = status == GroupMatchingStatus.DECLINED,
            joined = status == GroupMatchingStatus.JOINED_GROUP,
            pending = status == GroupMatchingStatus.WAITING_FOR_GROUP,
        )
    }

    private data class GroupFlags(
        val declined: Boolean,
        val joined: Boolean,
        val pending: Boolean,
    )
}
