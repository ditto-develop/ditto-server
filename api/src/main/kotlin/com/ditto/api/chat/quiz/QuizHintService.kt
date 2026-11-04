package com.ditto.api.chat.quiz

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.quiz.stat.QuizResultSchedule
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 열린 방에 첫 대화 힌트를 남긴다(IMP-1 BE-4). 생명주기 스케줄러가 개방 직후 매 주기 부른다.
 *
 * 개방 직후 생성과 누락 복구가 같은 경로다 — "열려 있는데 힌트가 없는 방"을 찾아 만든다. 예약 개방된 방은 같은 주기에,
 * 주말 중 수락으로 처음부터 열린 방은 다음 주기(1분 안)에 잡힌다. 방당 1개는 `chat_quiz_hint` 유니크가 보장한다.
 *
 * `QUIZ_CHAT` 플래그가 꺼져 있으면 아무것도 하지 않고, **켠 시각 이후 열린 방만** 대상으로 한다 — 주말 도중에 켜도
 * 이미 대화 중인 방에 힌트가 끼어들지 않는다.
 *
 * 트랜잭션을 열지 않는다 — 방마다 [QuizHintWriter]가 자기 트랜잭션을 열어 한 방의 실패가 다른 방을 막지 않는다.
 */
@Service
class QuizHintService(
    private val featureFlags: FeatureFlags,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
    private val quizSetRepository: QuizSetRepository,
    private val quizStatSnapshotService: QuizStatSnapshotService,
    private val quizHintWriter: QuizHintWriter,
) {
    /** @return 이번에 남긴 힌트 메시지. 호출자가 방 토픽으로 브로드캐스트한다. */
    fun createMissing(now: LocalDateTime): List<ChatMessageResponse> {
        val since = featureFlags.enabledAt(FeatureFlagKey.QUIZ_CHAT) ?: return emptyList()

        return chatRoomRepository.findAllIdsActiveWithoutHintOpenedSince(since).mapNotNull { roomId ->
            runCatchingExceptions {
                prepareSnapshot(roomId, now)
                quizHintWriter.write(roomId, now)
            }
                .onFailure { logger.warn(it) { "첫 대화 힌트 생성 실패 — 다음 주기에 다시 시도한다: roomId=$roomId" } }
                .getOrNull()
        }.also { if (it.isNotEmpty()) logger.info { "첫 대화 힌트 생성: ${it.size}건" } }
    }

    private fun prepareSnapshot(roomId: Long, now: LocalDateTime) {
        val room = chatRoomRepository.findById(roomId).orElse(null) ?: return
        val quizSet = chatRoomQuizSetResolver.quizSetIdOf(room)?.let { quizSetRepository.findById(it).orElse(null) } ?: return
        if (QuizResultSchedule.isOpen(quizSet, now)) {
            quizStatSnapshotService.getOrCreate(quizSet.id)
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
