package com.ditto.api.chat.quiz

import com.ditto.api.system.featureflag.FeatureFlagEnableListener
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * `QUIZ_CHAT`을 켜는 순간 **이미 열려 있는 방**에 빈 힌트 표시를 남긴다. 그 방들은 대화가 진행 중이라
 * 힌트가 뒤늦게 끼어들면 안 된다. 켠 뒤 열리는 방(예약 개방·주말 중 수락)만 힌트를 받는다.
 *
 * "켠 시각 이후 열린 방"을 시각으로 비교하지 않는 이유: 켠 시각은 서버 시각(어드민 오버라이드)이고 방의 열린 순간은
 * `opens_at`(서버 시각 또는 재매칭은 실제 시각)과 `created_at`(실제 시각)에 흩어져 있어, 비교하면 두 시계가 섞인다.
 */
@Component
class QuizChatEnableListener(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
) : FeatureFlagEnableListener {

    override val key: FeatureFlagKey = FeatureFlagKey.QUIZ_CHAT

    override fun onEnabled() {
        val openRoomIds = chatRoomRepository.findAllIdsActiveWithoutHint()
        chatQuizHintRepository.saveAll(openRoomIds.map { ChatQuizHint.of(it, messageId = null) })
        logger.info { "QUIZ_CHAT 켬 — 이미 열린 방 ${openRoomIds.size}개는 첫 대화 힌트에서 뺀다: $openRoomIds" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
