package com.ditto.api.match.service

import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.dto.GroupMatchJoinResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GroupMatchService(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
) {

    /** 그룹 매칭 참여 */
    @Transactional
    fun joinGroupMatch(memberId: Long, request: GroupMatchJoinRequest): GroupMatchJoinResponse {
        val quizSetId = request.quizSetId

        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
        }

        if (groupMatchMemberRepository.existsByMemberIdAndQuizSetId(memberId, quizSetId)) {
            throw WarnException(ErrorCode.ALREADY_JOINED_GROUP)
        }

        val room = findOrCreateRoom(quizSetId)
        room.addParticipant()
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        return GroupMatchJoinResponse.from(room)
    }

    /** 그룹 매칭 거절 */
    @Transactional
    fun declineGroupMatch(memberId: Long, request: GroupMatchDeclineRequest) {
        val quizSetId = request.quizSetId

        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
        }

        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))
    }

    /** 참여 가능한 방이 있으면 반환, 없으면 새 방 생성 */
    private fun findOrCreateRoom(quizSetId: Long): GroupMatch {
        val existingRoom = groupMatchRepository
            .findFirstByQuizSetIdAndIsActiveFalseOrderByCreatedAtAsc(quizSetId)

        return existingRoom ?: groupMatchRepository.save(GroupMatch.create(quizSetId))
    }
}
