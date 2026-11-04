package com.ditto.api.admin.qa

import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.matchers.string.shouldStartWith
import java.time.LocalDateTime
import javax.sql.DataSource

class QaHintStatesTest(
    private val qaHintStates: QaHintStates,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

    "힌트 상태와 이유를 보여 준다" {
        val open = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 1L))
        qaHintStates.of(open) shouldStartWith "없음 — QUIZ_CHAT"

        featureFlagAdminService.set(FeatureFlagKey.QUIZ_CHAT, enabled = true, adminMemberId = 1L)
        // 켤 때 이미 열린 방은 빈 표시가 남고, 원본 매칭이 없어 그 이유로 보인다.
        qaHintStates.of(open) shouldStartWith "빈 표시 — 원본 매칭이 없어"

        val thursday = LocalDateTime.of(2026, 3, 12, 12, 0)
        val scheduled = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 2L, now = thursday, period = ChatPeriod.weekendOf(thursday)))
        qaHintStates.of(scheduled) shouldStartWith "대기 — 방이 열리면"

        val active = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 3L))
        qaHintStates.of(active) shouldStartWith "대기 — 다음 주기"

        chatQuizHintRepository.save(ChatQuizHint.of(active.id, messageId = 42L))
        qaHintStates.of(active) shouldStartWith "만듦 — 메시지 #42"

        val ended = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 4L).apply { expire(LocalDateTime.of(2026, 3, 16, 0, 0)) })
        qaHintStates.of(ended) shouldStartWith "없음 — 힌트 없이"
    }

    "첫 대화 힌트 본문에서 kind 를 읽어 뜻을 붙인다" {
        QaSystemMessageMeaning.ofHint("""{"quizId":1,"kind":"SPLIT"}""") shouldStartWith "첫 대화 힌트(SPLIT)"
        QaSystemMessageMeaning.ofHint("{}") shouldStartWith "첫 대화 힌트"
    }
})
