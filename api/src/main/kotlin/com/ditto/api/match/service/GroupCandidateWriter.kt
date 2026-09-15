package com.ditto.api.match.service

import com.ditto.api.match.matching.ScoredMatch
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 배치가 계산한 후보 그룹을 저장한다.
 *
 * 1:1은 후보(`match_candidate`)와 성사(`personal_match`)가 다른 테이블이라 후보만 지우고 다시 깔면 되지만,
 * 그룹은 `group_match` 하나가 후보이자 성사 상태다. 그래서 재생성이 이미 수락된 그룹을 지우면
 * 열려 있는 채팅방(`chat_room.source_id`)이 가리킬 곳을 잃는다.
 * 응답이 하나라도 시작된 퀴즈셋은 통째로 거부한다 — 일부만 갈아끼우면 후보 구성이 뒤섞인다.
 * 조용히 건너뛰지 않고 예외로 알리는 이유: 어드민이 재생성 버튼을 눌렀는데 성공처럼 보이면 안 된다.
 */
@Component
class GroupCandidateWriter(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    /**
     * 퀴즈셋의 후보 그룹을 [groups]로 대체한다. 아무도 응답하지 않은 경우에만 수행한다.
     *
     * @throws WarnException 이미 응답(수락·거절·성사)이 시작된 퀴즈셋이면 아무것도 바꾸지 않고 던진다
     */
    @Transactional
    fun replace(quizSetId: Long, groups: List<ScoredMatch>): CandidateRowCounts {
        val existingRooms = groupMatchRepository.findByQuizSetId(quizSetId)
        val existingMembers = findMembersOf(existingRooms)
        if (hasAnyResponse(existingRooms, existingMembers)) {
            throw WarnException(
                ErrorCode.MATCH_CANDIDATES_ALREADY_RESPONDED,
                "${ErrorCode.MATCH_CANDIDATES_ALREADY_RESPONDED.message} (기존 그룹 ${existingRooms.size}개)",
            )
        }

        deleteRooms(existingRooms.map { it.id })
        groups.forEach { group -> saveRoom(quizSetId, group) }
        return CandidateRowCounts(
            deletedCount = existingRooms.size + existingMembers.size,
            savedCount = groups.size + groups.sumOf { it.memberIds.size },
        )
    }

    private fun findMembersOf(rooms: List<GroupMatch>): List<GroupMatchMember> {
        if (rooms.isEmpty()) return emptyList()
        return groupMatchMemberRepository.findByRoomIdIn(rooms.map { it.id })
    }

    /** 활성화된 방이 있거나 대기가 아닌 멤버가 하나라도 있으면 이미 응답이 시작된 것이다. */
    private fun hasAnyResponse(existingRooms: List<GroupMatch>, existingMembers: List<GroupMatchMember>): Boolean =
        existingRooms.any { it.isActive } || existingMembers.any { it.status != InvitationStatus.PENDING }

    private fun deleteRooms(roomIds: List<Long>) {
        if (roomIds.isEmpty()) return
        groupMatchMemberRepository.deleteByRoomIdIn(roomIds)
        groupMatchRepository.deleteAllByIdInBatch(roomIds)
    }

    private fun saveRoom(quizSetId: Long, group: ScoredMatch) {
        val room = groupMatchRepository.save(GroupMatch.candidate(quizSetId, group.score))
        groupMatchMemberRepository.saveAll(
            group.memberIds.map { memberId -> GroupMatchMember.candidate(room.id, memberId) },
        )
    }
}
