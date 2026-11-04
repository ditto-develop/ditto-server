package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.quiz.stat.QuizAnswerSheetLoader
import com.ditto.api.quiz.stat.QuizResultSchedule
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 방 하나의 첫 대화 힌트를 만든다. 힌트 메시지와 방당 1개 표시(`chat_quiz_hint`)를 한 트랜잭션에 담아,
 * 겹쳐 돈 스케줄러가 같은 방에 힌트를 두 번 남기지 못하게 표시의 유니크가 막는다(진 쪽은 통째로 롤백).
 *
 * 퀴즈셋 스냅샷은 **이 트랜잭션 밖에서 먼저 만들어 둬야 한다**([QuizHintService]). 안에서 지연 생성하면
 * 생성은 별도 트랜잭션으로 커밋되지만, 이 트랜잭션의 읽기 스냅샷(MySQL REPEATABLE READ)이 이미 고정돼 그 행을 못 본다.
 */
@Component
class QuizHintWriter(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
    private val quizSetRepository: QuizSetRepository,
    private val quizAnswerSheetLoader: QuizAnswerSheetLoader,
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val objectMapper: ObjectMapper,
) {
    /**
     * @return 남긴 힌트 메시지. 이미 있거나 고를 문항이 없으면 null(표시는 남긴다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(roomId: Long, now: LocalDateTime): ChatMessageResponse? {
        if (chatQuizHintRepository.existsByRoomId(roomId)) return null
        val room = chatRoomRepository.findById(roomId).orElse(null) ?: return null
        // 기준 멤버는 열리는 순간 남아 있는 사람이다. 이후 합류자는 이미 남은 힌트를 본다.
        val memberIds = chatRoomMemberRepository.findByRoomId(roomId).filterNot(ChatRoomMember::hasLeft).map { it.memberId }

        val content = chatRoomQuizSetResolver.quizSetIdOf(room)?.let { quizSetId ->
            val sheet = quizAnswerSheetLoader.load(quizSetId, memberIds)
            val snapshot = quizSetRepository.findById(quizSetId).orElse(null)
                ?.takeIf { QuizResultSchedule.isOpen(it, now) }
                ?.let { quizStatSnapshotService.getOrCreate(quizSetId) }
            if (room.sourceType == ChatRoomType.GROUP) {
                QuizHintComposer.forGroup(sheet, memberIds, snapshot)
            } else {
                QuizHintComposer.forPair(sheet, memberIds, snapshot)
            }
        }

        val message = content?.let {
            chatMessageRepository.save(ChatMessage.quizHint(roomId, objectMapper.writeValueAsString(it)))
        }
        chatQuizHintRepository.saveAndFlush(ChatQuizHint.of(roomId, message?.id))
        return message?.let { ChatMessageResponse.serverGenerated(it) }
    }
}
