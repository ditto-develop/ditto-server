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
    private val controller = ChatStompController(chatService, messagingTemplate, chatMessageNotifier)

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
        )
        every { chatService.sendMessage(2L, 1L, "안녕", ChatMessageType.TEXT) } returns response
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        controller.sendMessage(roomId = 1L, request = ChatSendRequest("안녕"), principal = principal)

        verify { chatService.sendMessage(2L, 1L, "안녕", ChatMessageType.TEXT) }
        verify { messagingTemplate.convertAndSend("/sub/chat/rooms/1", response) }
    }

    @Test
    @DisplayName("principal 이 MemberPrincipal 이 아니면 UNAUTHORIZED_ERROR")
    fun sendMessageBadPrincipal() {
        val principal = mockk<Principal>()

        shouldThrow<WarnException> {
            controller.sendMessage(roomId = 1L, request = ChatSendRequest("안녕"), principal = principal)
        }.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
    }
}
