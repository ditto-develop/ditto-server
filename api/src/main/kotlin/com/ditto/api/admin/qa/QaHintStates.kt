package com.ditto.api.admin.qa

import com.ditto.api.chat.quiz.ChatRoomQuizSetResolver
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import org.springframework.stereotype.Component

/**
 * QA 화면에 보일 방의 첫 대화 힌트 상태와 그 이유. "왜 힌트가 안 생겼나"를 로그 없이 알 수 있게 한다.
 * 규칙은 `docs/domains/chat.md` "퀴즈 메시지".
 */
@Component
class QaHintStates(
    private val featureFlags: FeatureFlags,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    private val chatRoomQuizSetResolver: ChatRoomQuizSetResolver,
) {
    fun of(room: ChatRoom): String {
        val mark = chatQuizHintRepository.findByRoomIdIn(listOf(room.id)).firstOrNull()
        return when {
            mark?.messageId != null -> "만듦 — 메시지 #${mark.messageId}"
            mark != null && chatRoomQuizSetResolver.quizSetIdOf(room) == null -> "빈 표시 — 원본 매칭이 없어 고를 문항이 없었음"
            mark != null -> "빈 표시 — QUIZ_CHAT 을 켤 때 이미 열려 있던 방이라 뺐음(또는 고를 문항이 없었음)"
            !featureFlags.isEnabled(FeatureFlagKey.QUIZ_CHAT) -> "없음 — QUIZ_CHAT 기능 플래그가 꺼져 있음"
            room.status == ChatRoomStatus.SCHEDULED -> "대기 — 방이 열리면 그 주기에 만듦"
            room.status == ChatRoomStatus.ACTIVE -> "대기 — 다음 주기(1분 안)에 만듦"
            else -> "없음 — 힌트 없이 끝난 방"
        }
    }
}
