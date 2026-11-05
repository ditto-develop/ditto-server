package com.ditto.api.chat.quiz

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.rematch.repository.RematchRepository
import org.springframework.stereotype.Component

/**
 * 방을 만든 매칭의 퀴즈셋. 방은 퀴즈셋을 직접 들지 않고 `source_type`/`source_id`로 원본 매칭을 가리킨다.
 *
 * 재매칭 방은 그 주 **그룹 퀴즈**(`rematch.quiz_set_id`)가 기준이다 — 두 사람이 함께 푼 마지막 퀴즈가 그것이다.
 */
@Component
class ChatRoomQuizSetResolver(
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
) {
    /** 원본 매칭이 지워졌으면(QA 초기화 등) null. */
    fun quizSetIdOf(room: ChatRoom): Long? =
        when (room.sourceType) {
            ChatRoomType.PERSONAL -> personalMatchRepository.findById(room.sourceId).orElse(null)?.quizSetId
            ChatRoomType.GROUP -> groupMatchRepository.findById(room.sourceId).orElse(null)?.quizSetId
            ChatRoomType.REMATCH -> rematchRepository.findById(room.sourceId).orElse(null)?.quizSetId
        }

    /** [quizSetIdOf]와 같되 없으면 `CHAT_ROOM_QUIZ_SET_NOT_FOUND`. 답변 보기·질문 카드가 같은 코드로 답한다. */
    fun requireQuizSetIdOf(room: ChatRoom): Long =
        quizSetIdOf(room) ?: throw WarnException(ErrorCode.CHAT_ROOM_QUIZ_SET_NOT_FOUND)
}
