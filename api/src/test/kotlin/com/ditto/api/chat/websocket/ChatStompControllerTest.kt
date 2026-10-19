package com.ditto.api.chat.websocket

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.service.ChatService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessageType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.security.Principal
import java.time.LocalDateTime

class ChatStompControllerTest {

    private val chatService = mockk<ChatService>()
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val chatMessageNotifier = mockk<ChatMessageNotifier>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val controller = ChatStompController(chatService, messagingTemplate, chatMessageNotifier, meterRegistry)

    private fun sentCount(outcome: String): Double =
        meterRegistry.find(ChatStompController.SEND_METRIC).tag("outcome", outcome).counter()?.count() ?: 0.0

    @Test
    @DisplayName("전송 전에도 결과별 카운터가 0으로 등록돼 있다")
    fun sendCountersStartAtZero() {
        listOf(
            ChatStompController.OUTCOME_SUCCESS,
            ChatStompController.OUTCOME_REJECTED,
            ChatStompController.OUTCOME_ERROR,
        ).forEach { outcome ->
            meterRegistry.find(ChatStompController.SEND_METRIC).tag("outcome", outcome).counter()?.count() shouldBe 0.0
        }
    }

    @Test
    @DisplayName("메시지를 저장하고 방 토픽으로 브로드캐스트한다")
    fun sendMessage() {
        val response = ChatMessageResponse(
            id = 10L,
            roomId = 1L,
            senderId = 2L,
            messageType = ChatMessageType.TEXT,
            content = "안녕",
            imageUrl = null,
            createdAt = LocalDateTime.of(2026, 7, 16, 12, 0),
            unreadCount = 1,
        )
        every { chatService.sendMessage(2L, 1L, "안녕", ChatMessageType.TEXT) } returns response
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        controller.sendMessage(roomId = 1L, request = ChatSendRequest("안녕"), principal = principal)

        verify { chatService.sendMessage(2L, 1L, "안녕", ChatMessageType.TEXT) }
        verify { messagingTemplate.convertAndSend("/sub/chat/rooms/1", response) }
        sentCount(ChatStompController.OUTCOME_SUCCESS) shouldBe 1.0
    }

    @Test
    @DisplayName("principal 이 MemberPrincipal 이 아니면 UNAUTHORIZED_ERROR")
    fun sendMessageBadPrincipal() {
        val principal = mockk<Principal>()

        shouldThrow<WarnException> {
            controller.sendMessage(roomId = 1L, request = ChatSendRequest("안녕"), principal = principal)
        }.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
        sentCount(ChatStompController.OUTCOME_REJECTED) shouldBe 1.0
    }

    @Test
    @DisplayName("저장 중 서버 오류가 나면 실패로 세고 예외를 그대로 던진다")
    fun sendMessageServerError() {
        every { chatService.sendMessage(2L, 1L, "안녕", ChatMessageType.TEXT) } throws IllegalStateException("db down")
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        shouldThrow<IllegalStateException> {
            controller.sendMessage(roomId = 1L, request = ChatSendRequest("안녕"), principal = principal)
        }
        sentCount(ChatStompController.OUTCOME_ERROR) shouldBe 1.0
        sentCount(ChatStompController.OUTCOME_SUCCESS) shouldBe 0.0
        verify(exactly = 0) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
    }
}
