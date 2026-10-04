package com.ditto.api.admin.qa

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 방 목록의 출처 문구. 화면에서 테스트한 방을 찾는 단서다. */
@Component
@Transactional(readOnly = true)
class QaRoomSourceLabels(
    private val groupMatchRepository: GroupMatchRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
) {
    /** 방 id 별 문구. 원본을 찾지 못한 방은 빠진다. */
    fun of(rooms: List<ChatRoom>): Map<Long, String> {
        val sources = Sources(
            quizSetIdByGroupMatchId = groupMatchRepository.findAllById(sourceIdsOf(rooms, ChatRoomType.GROUP))
                .associate { it.id to it.quizSetId },
            quizSetIdByPersonalMatchId = personalMatchRepository.findAllById(sourceIdsOf(rooms, ChatRoomType.PERSONAL))
                .associate { it.id to it.quizSetId },
        )
        val quizSetTitles = quizSetRepository.findAllById(sources.quizSetIds()).associate { it.id to it.title }
        return rooms.mapNotNull { room -> sources.labelOf(room, quizSetTitles)?.let { room.id to it } }.toMap()
    }

    private fun sourceIdsOf(rooms: List<ChatRoom>, type: ChatRoomType): List<Long> =
        rooms.filter { it.sourceType == type }.map { it.sourceId }

    private class Sources(
        private val quizSetIdByGroupMatchId: Map<Long, Long>,
        private val quizSetIdByPersonalMatchId: Map<Long, Long>,
    ) {
        fun quizSetIds(): Set<Long> = (quizSetIdByGroupMatchId.values + quizSetIdByPersonalMatchId.values).toSet()

        /** 그룹 방은 그룹 번호와 퀴즈셋, 1:1 방은 퀴즈셋, 재매칭 방은 쌍 번호. */
        fun labelOf(room: ChatRoom, quizSetTitles: Map<Long, String>): String? =
            when (room.sourceType) {
                ChatRoomType.GROUP ->
                    quizSetIdByGroupMatchId[room.sourceId]?.let { "그룹 #${room.sourceId} · ${quizSetTitles[it]}" }
                ChatRoomType.PERSONAL -> quizSetIdByPersonalMatchId[room.sourceId]?.let { quizSetTitles[it] }
                ChatRoomType.REMATCH -> "재매칭 #${room.sourceId}"
            }
    }
}
