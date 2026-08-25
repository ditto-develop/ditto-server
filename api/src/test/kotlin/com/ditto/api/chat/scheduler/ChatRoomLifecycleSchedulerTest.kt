package com.ditto.api.chat.scheduler

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.notification.notifier.ChatEndingSoonNotifier
import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.api.review.service.EndedChatReviewOpener
import com.ditto.api.system.ServerTimeProvider
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime

/** 실제 시각과 확연히 달라, 넘어온 값만 보고 어느 시계인지 가릴 수 있는 오버라이드 시각. */
private val OVERRIDDEN_NOW = LocalDateTime.of(2020, 1, 1, 0, 0)

/**
 * 어느 호출이 어느 시계를 받는지 고정한다 — 저장(`opens_at`)은 실제 시각, 상태 전이 판단은 서버 시각(#146).
 * 규칙이 코드에만 있으면 누가 한쪽으로 통일해도 드러나지 않는다.
 */
class ChatRoomLifecycleSchedulerTest : FreeSpec({
    val chatRoomEndService = mockk<ChatRoomEndService>(relaxed = true)
    val endedChatReviewOpener = mockk<EndedChatReviewOpener>(relaxed = true)
    val rematchChatRoomOpener = mockk<RematchChatRoomOpener>(relaxed = true)
    val reviewRequestNotifier = mockk<ReviewRequestNotifier>(relaxed = true)
    val chatEndingSoonNotifier = mockk<ChatEndingSoonNotifier>(relaxed = true)
    val serverTimeProvider = mockk<ServerTimeProvider>()

    val scheduler = ChatRoomLifecycleScheduler(
        chatRoomEndService,
        endedChatReviewOpener,
        rematchChatRoomOpener,
        reviewRequestNotifier,
        chatEndingSoonNotifier,
        serverTimeProvider,
    )

    // 호출 기록만 지운다(answers = false) — 기록이 쌓이면 다음 테스트의 verify 가 이전 호출까지 본다.
    beforeTest {
        clearMocks(
            chatRoomEndService,
            endedChatReviewOpener,
            rematchChatRoomOpener,
            reviewRequestNotifier,
            chatEndingSoonNotifier,
            serverTimeProvider,
            answers = false,
        )
        every { serverTimeProvider.now() } returns OVERRIDDEN_NOW
    }

    "상태 전이 판단은 서버 시각을 쓴다 — 어드민이 시각을 옮기면 방이 열리고 닫힌다" {
        scheduler.sweep()

        verify { chatRoomEndService.openDue(OVERRIDDEN_NOW) }
        verify { chatRoomEndService.endExpired(OVERRIDDEN_NOW) }
        verify { chatEndingSoonNotifier.notifyEndingSoon(OVERRIDDEN_NOW) }
    }

    "재매칭 예약은 실제 시각을 쓴다 — opens_at 에 가짜 시각이 저장되면 오버라이드를 꺼도 방이 미래에 갇힌다" {
        val reservedAt = slot<LocalDateTime>()

        scheduler.sweep()

        verify { rematchChatRoomOpener.openMissing(capture(reservedAt)) }
        (reservedAt.captured == OVERRIDDEN_NOW) shouldBe false
        (reservedAt.captured > LocalDateTime.of(2026, 1, 1, 0, 0)) shouldBe true
    }

    "마감된 방은 평가 개방과 평가 요청 알림으로 이어진다" {
        scheduler.sweep()

        verify { endedChatReviewOpener.openFor(emptyList()) }
        verify { endedChatReviewOpener.openMissing() }
        verify { reviewRequestNotifier.notifyFor(emptyList()) }
    }
})
