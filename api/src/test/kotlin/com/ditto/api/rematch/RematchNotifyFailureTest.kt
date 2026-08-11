package com.ditto.api.rematch

import com.ditto.api.chat.service.ChatService
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException

/**
 * 성사 알림이 실패해도 방 예약을 성공으로 세는지 본다.
 *
 * 방은 자기 트랜잭션에서 이미 커밋됐고, 예약 조회(`findMatchedWithoutChatRoom`)는 방이 없는 쌍만
 * 집으므로 그 쌍은 다음 주기에 다시 오지 않는다. 여기서 실패로 세면 로그가 "다음 주기로 넘긴다"고
 * 남기지만 실제로는 아무도 다시 시도하지 않는다.
 */
class RematchNotifyFailureTest {

    private val rematchRepository = mockk<RematchRepository>()
    private val chatService = mockk<ChatService>()
    private val memberRepository = mockk<MemberRepository>()
    private val notificationAppender = mockk<NotificationAppender>(relaxed = true)

    private val rematchChatRoomOpener = RematchChatRoomOpener(
        rematchRepository,
        chatService,
        memberRepository,
        notificationAppender,
    )

    @Test
    @DisplayName("성사 알림이 실패해도 방 예약은 성공으로 센다")
    fun countsReservedRoomWhenNotifyFails() {
        every { rematchRepository.findMatchedWithoutChatRoom(any()) } returns listOf(matchedRematch())
        every { memberRepository.countByIdInAndStatus(any(), any()) } returns 0
        every { chatService.createRematchRoom(any(), any(), any()) } returns CHAT_ROOM_ID
        every { memberRepository.findAllById(any()) } throws
            DataAccessResourceFailureException("커넥션을 얻지 못했습니다")

        rematchChatRoomOpener.openMissing(BEFORE_WEEKEND) shouldBe 1

        verify { chatService.createRematchRoom(any(), any(), any()) }
    }

    private fun matchedRematch(): Rematch = RematchFixture.create(
        memberIdA = MEMBER_A,
        memberIdB = MEMBER_B,
        week = OperationWeek(MATCH_WEEK),
    ).apply {
        submitWants(MEMBER_A, wants = true, now = MATCHED_ON_MONDAY)
        submitWants(MEMBER_B, wants = true, now = MATCHED_ON_MONDAY)
    }

    companion object {
        private val MATCH_WEEK = LocalDate.of(2026, 3, 9)
        private val MATCHED_ON_MONDAY = LocalDateTime.of(2026, 3, 9, 10, 0)

        /** 성사 주말이 열리기 전. 예약이 곧바로 도는 정상 경로다. */
        private val BEFORE_WEEKEND = LocalDateTime.of(2026, 3, 9, 10, 1)
        private const val MEMBER_A = 1L
        private const val MEMBER_B = 2L
        private const val CHAT_ROOM_ID = 100L
    }
}
