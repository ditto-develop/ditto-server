package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 열린 방에 첫 대화 힌트를 남긴다(IMP-1 BE-4). 생명주기 스케줄러가 개방 직후 매 주기 부른다.
 *
 * 개방 직후 생성과 누락 복구가 같은 경로다 — "열려 있는데 힌트 표시가 없는 방"을 찾아 만든다. 예약 개방된 방은 같은 주기에,
 * 주말 중 수락으로 처음부터 열린 방은 다음 주기(1분 안)에 잡힌다. 방당 1개는 `chat_quiz_hint` 유니크가 보장한다.
 *
 * `QUIZ_CHAT` 플래그가 꺼져 있으면 아무것도 하지 않는다. 켜는 순간 이미 열려 있던 방은 빈 표시가 남아 대상이 아니다
 * ([QuizChatEnableListener]) — 주말 도중에 켜도 대화 중인 방에 힌트가 끼어들지 않는다.
 *
 * 트랜잭션을 열지 않는다 — 방마다 [QuizHintWriter]가 자기 트랜잭션을 열어 한 방의 실패가 다른 방을 막지 않는다.
 */
@Service
class QuizHintService(
    private val featureFlags: FeatureFlags,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val quizHintWriter: QuizHintWriter,
) {
    /** @return 이번에 남긴 힌트 메시지. 호출자가 방 토픽으로 브로드캐스트한다. */
    fun createMissing(now: LocalDateTime): List<ChatMessageResponse> {
        if (!featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT)) return emptyList()

        val results = chatRoomRepository.findAllIdsActiveWithoutHint().associateWith { roomId ->
            runCatchingExceptions { writeFor(roomId, now) }
                .onFailure { logger.warn(it) { "첫 대화 힌트 생성 실패 — 다음 주기에 다시 시도한다: roomId=$roomId" } }
                .getOrNull()
        }
        val created = results.values.filterIsInstance<QuizHintWrite.Created>().map { it.message }
        val emptyMarked = results.filterValues { it == QuizHintWrite.EmptyMark }.keys
        if (created.isNotEmpty()) logger.info { "첫 대화 힌트 생성: 방 ${created.map { it.roomId }}" }
        if (emptyMarked.isNotEmpty()) logger.info { "첫 대화 힌트로 고를 문항이 없어 빈 표시만 남김: 방 $emptyMarked" }
        return created
    }

    /**
     * QA 용: 방의 힌트 표시와 힌트 메시지를 지운다. 방이 열려 있고 기능이 켜져 있으면 다음 주기(1분 안)에 다시 만들어진다.
     * 이미 화면에 받은 힌트 메시지는 새로고침 전까지 남아 보인다.
     *
     * @return 지운 표시가 있었으면 true
     */
    @Transactional
    fun resetHint(roomId: Long): Boolean {
        val marks = chatQuizHintRepository.findByRoomIdIn(listOf(roomId))
        marks.mapNotNull { it.messageId }.takeIf { it.isNotEmpty() }?.let(chatMessageRepository::deleteAllByIdInBatch)
        chatQuizHintRepository.deleteAll(marks)
        return marks.isNotEmpty()
    }

    private fun writeFor(roomId: Long, now: LocalDateTime): QuizHintWrite {
        val room = chatRoomRepository.findById(roomId).orElse(null) ?: return QuizHintWrite.Skipped
        val quizSetId = chatRoomQuizSetResolver.quizSetIdOf(room)
        val snapshot = quizSetId?.let { quizStatSnapshotService.findIfClosed(it, now) }
        return quizHintWriter.write(room, quizSetId, snapshot)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
