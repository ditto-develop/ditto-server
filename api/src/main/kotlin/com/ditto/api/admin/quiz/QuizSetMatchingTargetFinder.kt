package com.ditto.api.admin.quiz

import com.ditto.api.admin.cleanup.ChatRoomEraser
import com.ditto.api.admin.cleanup.MatchingRecordTargets
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import org.springframework.stereotype.Component

/** 퀴즈셋 하나에서 이어진 1:1 신청·그룹·재매칭과 거기서 열린 채팅방을 찾는다. */
@Component
class QuizSetMatchingTargetFinder(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
    private val chatRoomEraser: ChatRoomEraser,
) {
    fun findTargetsOf(quizSetId: Long): MatchingRecordTargets {
        val personalMatchIds = personalMatchRepository.findByQuizSetIdIn(listOf(quizSetId)).map { it.id }.toSet()
        val groupMatchIds = groupMatchRepository.findByQuizSetId(quizSetId).map { it.id }.toSet()
        val rematchIds = findRematchIdsFrom(groupMatchIds)
        val roomIds = chatRoomEraser.findRoomIdsFrom(ChatRoomType.PERSONAL, personalMatchIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.GROUP, groupMatchIds) +
            chatRoomEraser.findRoomIdsFrom(ChatRoomType.REMATCH, rematchIds)
        return MatchingRecordTargets(roomIds, personalMatchIds, groupMatchIds, rematchIds)
    }

    // 재매칭은 그룹 방이 끝날 때 그 그룹의 퀴즈셋으로만 생기므로 그룹으로 찾으면 빠짐이 없다.
    private fun findRematchIdsFrom(groupMatchIds: Set<Long>): Set<Long> {
        if (groupMatchIds.isEmpty()) return emptySet()
        return rematchRepository.findAllBySourceGroupMatchIdIn(groupMatchIds).map { it.id }.toSet()
    }
}
