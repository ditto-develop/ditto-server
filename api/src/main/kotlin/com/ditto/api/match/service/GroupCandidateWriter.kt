package com.ditto.api.match.service

import com.ditto.api.match.matching.ScoredMatch
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * 배치가 계산한 후보 그룹을 저장한다.
 *
 * 1:1은 후보(`match_candidate`)와 성사(`personal_match`)가 다른 테이블이라 후보만 지우고 다시 깔면 되지만,
 * 그룹은 `group_match` 하나가 후보이자 성사 상태다. 그래서 재생성이 이미 수락된 그룹을 지우면
 * 열려 있는 채팅방(`chat_room.source_id`)이 가리킬 곳을 잃는다.
 * 응답이 하나라도 시작된 퀴즈셋은 통째로 건너뛴다 — 일부만 갈아끼우면 후보 구성이 뒤섞인다.
 */
@Component
class GroupCandidateWriter(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
) {

    /** 퀴즈셋의 후보 그룹을 [groups]로 대체한다. 아무도 응답하지 않은 경우에만 수행한다. */
    @Transactional
    fun replace(quizSetId: Long, groups: List<ScoredMatch>) {
        val existingRooms = groupMatchRepository.findByQuizSetId(quizSetId)
        val existingRoomIds = existingRooms.map { it.id }

        if (hasAnyResponse(existingRooms, existingRoomIds)) {
            logger.warn {
                "이미 응답이 시작된 퀴즈셋이라 후보 그룹 재생성을 건너뛴다: " +
                    "quizSetId=$quizSetId, 기존 그룹=${existingRooms.size}개"
            }
            return
        }

        deleteRooms(existingRoomIds)
        groups.forEach { group -> saveRoom(quizSetId, group) }
        logger.info { "후보 그룹 생성: quizSetId=$quizSetId, ${groups.size}개" }
    }

    /** 활성화된 방이 있거나 대기가 아닌 멤버가 하나라도 있으면 이미 응답이 시작된 것이다. */
    private fun hasAnyResponse(existingRooms: List<GroupMatch>, existingRoomIds: List<Long>): Boolean {
        if (existingRooms.any { it.isActive }) return true
        if (existingRoomIds.isEmpty()) return false
        return groupMatchMemberRepository.findByRoomIdIn(existingRoomIds)
            .any { it.status != InvitationStatus.PENDING }
    }

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
