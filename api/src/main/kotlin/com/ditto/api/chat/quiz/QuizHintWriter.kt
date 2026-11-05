package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.quiz.stat.QuizAnswerSheetLoader
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 방 하나의 첫 대화 힌트를 만든다. 힌트 메시지와 방당 1개 표시(`chat_quiz_hint`)를 한 트랜잭션에 담아,
 * 겹쳐 돈 스케줄러가 같은 방에 힌트를 두 번 남기지 못하게 표시의 유니크가 막는다(진 쪽은 통째로 롤백).
 *
 * 퀴즈셋 스냅샷은 **넘겨받는다**([QuizHintService]가 이 트랜잭션 밖에서 만든다). 안에서 지연 생성하면 생성은 별도 트랜잭션으로
 * 커밋되지만, 이 트랜잭션의 읽기 스냅샷(MySQL REPEATABLE READ)이 이미 고정돼 그 행을 못 본다.
 */
@Component
class QuizHintWriter(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    private val quizAnswerSheetLoader: QuizAnswerSheetLoader,
    private val objectMapper: ObjectMapper,
) {
    /**
     * @param quizSetId 방의 퀴즈셋. 원본 매칭이 없으면 null — 고를 문항이 없어 빈 표시만 남긴다.
     * @return 남긴 힌트 메시지([QuizHintWrite.Created]), 고를 문항이 없어 빈 표시만 남겼으면 [QuizHintWrite.EmptyMark],
     *   이미 표시가 있었거나 방이 없으면 [QuizHintWrite.Skipped].
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(room: ChatRoom, quizSetId: Long?, snapshot: QuizStatSnapshot?): QuizHintWrite {
        if (chatQuizHintRepository.existsByRoomId(room.id)) return QuizHintWrite.Skipped
        // 기준 멤버는 열리는 순간 남아 있는 사람이다. 이후 합류자는 이미 남은 힌트를 본다.
        val memberIds = chatRoomMemberRepository.findByRoomIdAndLeftAtIsNull(room.id).map { it.memberId }

        val content = quizSetId?.let {
            val sheet = quizAnswerSheetLoader.load(it, memberIds)
            if (room.sourceType == ChatRoomType.GROUP) {
                QuizHintComposer.forGroup(sheet, memberIds, snapshot)
            } else {
                QuizHintComposer.forPair(sheet, memberIds, snapshot)
            }
        }

        val message = content?.let {
            chatMessageRepository.save(ChatMessage.quizHint(room.id, objectMapper.writeValueAsString(it)))
        }
        chatQuizHintRepository.saveAndFlush(ChatQuizHint.of(room.id, message?.id))
        return message?.let { QuizHintWrite.Created(ChatMessageResponse.serverGenerated(it)) } ?: QuizHintWrite.EmptyMark
    }
}

sealed interface QuizHintWrite {
    data class Created(val message: ChatMessageResponse) : QuizHintWrite

    data object EmptyMark : QuizHintWrite

    data object Skipped : QuizHintWrite
}
