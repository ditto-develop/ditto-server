package com.ditto.api.match.service

import com.ditto.api.match.GroupMatchingStatus
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.springframework.stereotype.Component

@Component
class GroupMatchingStatusReader(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    fun read(memberId: Long, groupQuizSetId: Long): GroupMatchingStatus {
        val invitations = groupMatchMemberRepository.findByMemberIdAndQuizSetId(memberId, groupQuizSetId)
        if (invitations.isEmpty()) return statusWithoutInvitation(groupQuizSetId)

        val accepted = invitations.firstOrNull { it.status == InvitationStatus.ACCEPTED }
        if (accepted == null) {
            val allDeclined = invitations.all { it.status == InvitationStatus.DECLINED }
            return if (allDeclined) GroupMatchingStatus.DECLINED else GroupMatchingStatus.NOT_RESPONDED
        }

        val group = groupMatchRepository.findById(accepted.roomId).orElseThrow {
            ErrorException(ErrorCode.INTERNAL_ERROR, "참여 기록의 그룹 방이 없습니다: roomId=${accepted.roomId}")
        }
        return if (group.isActive) GroupMatchingStatus.JOINED_GROUP else GroupMatchingStatus.WAITING_FOR_GROUP
    }

    // 배치가 끝났다는 기록이 따로 없어서 그 셋에 후보 그룹이 하나라도 있으면 끝난 것으로 본다.
    // 셋 전체가 0그룹이면 배치 전과 구분되지 않아 응답 마감까지 기다린다.
    private fun statusWithoutInvitation(groupQuizSetId: Long): GroupMatchingStatus =
        if (groupMatchRepository.existsByQuizSetId(groupQuizSetId)) {
            GroupMatchingStatus.NOT_INVITED
        } else {
            GroupMatchingStatus.NO_CANDIDATE_GROUPS
        }
}
