package com.ditto.api.notification

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException

/**
 * 알림을 남기려다 실패해도 부르는 쪽 흐름이 끊기지 않는지 본다.
 *
 * 적재 자체의 실패는 `NotificationAppender`가 흡수하지만([NotificationAppenderTest]), 그 앞의 조회는
 * 흡수 범위 밖이라 각 Notifier 가 스스로 막는다. 조회 실패는 실제 입력으로 만들 수 없어 mock 을 쓴다.
 */
class NotifierFailureTest {

    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val chatRoomMemberRepository = mockk<ChatRoomMemberRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val notificationAppender = mockk<NotificationAppender>(relaxed = true)

    private val reviewRequestNotifier = ReviewRequestNotifier(
        chatRoomRepository,
        chatRoomMemberRepository,
        memberRepository,
        notificationAppender,
    )
    private val chatMessageNotifier = ChatMessageNotifier(
        chatRoomMemberRepository,
        memberRepository,
        notificationAppender,
    )

    @Test
    @DisplayName("평가 요청 — 방 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun reviewRequestAbsorbsRoomQueryFailure() {
        every { chatRoomRepository.findAllById(any()) } throws connectionFailure()

        reviewRequestNotifier.notifyFor(listOf(ROOM_ID)) shouldBe 0
    }

    // 조회가 여럿이라 마지막 것까지 감싸였는지 따로 본다.
    @Test
    @DisplayName("평가 요청 — 닉네임 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun reviewRequestAbsorbsNicknameQueryFailure() {
        val room = ChatRoomFixture.personal()
        every { chatRoomRepository.findAllById(any()) } returns listOf(room)
        every { chatRoomMemberRepository.findByRoomIdIn(any()) } returns listOf(
            ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L),
            ChatRoomMemberFixture.create(roomId = room.id, memberId = 2L),
        )
        every { memberRepository.findAllById(any()) } throws connectionFailure()

        reviewRequestNotifier.notifyFor(listOf(room.id)) shouldBe 0
    }

    @Test
    @DisplayName("새 메시지 — 참여자 조회가 실패해도 예외 대신 0 을 돌려준다")
    fun chatMessageAbsorbsMemberQueryFailure() {
        every { chatRoomMemberRepository.findByRoomIdIn(any()) } throws connectionFailure()

        chatMessageNotifier.notifyNewMessage(textMessage()) shouldBe 0

        verify(exactly = 0) { notificationAppender.appendAll(any(), any(), any()) }
    }

    private fun connectionFailure() = DataAccessResourceFailureException("커넥션을 얻지 못했습니다")

    private fun textMessage() = ChatMessageResponse(
        id = 10L,
        roomId = ROOM_ID,
        senderId = 2L,
        messageType = ChatMessageType.TEXT,
        content = "안녕",
        imageUrl = null,
        createdAt = LocalDateTime.of(2026, 7, 16, 12, 0),
    )

    companion object {
        private const val ROOM_ID = 1L
    }
}
