package com.ditto.api.chat.websocket

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.dto.QuizCardRejectedEvent
import com.ditto.api.chat.quiz.QuizCardSendResult
import com.ditto.api.chat.quiz.QuizCardService
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
import io.mockk.verifyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.security.Principal
import java.time.LocalDateTime

class ChatStompControllerTest {

    private val chatService = mockk<ChatService>()
    private val quizCardService = mockk<QuizCardService>()
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val chatMessageNotifier = mockk<ChatMessageNotifier>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val controller = ChatStompController(chatService, quizCardService, messagingTemplate, chatMessageNotifier, meterRegistry)

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

    private fun message(id: Long, type: ChatMessageType, content: String) = ChatMessageResponse(
        id = id,
        roomId = 1L,
        senderId = 2L,
        messageType = type,
        content = content,
        imageUrl = null,
        createdAt = LocalDateTime.of(2026, 11, 6, 12, 0),
        unreadCount = 1,
    )

    @Test
    @DisplayName("질문 카드는 카드·한마디를 순서대로 방 토픽에 보내고 알림은 카드 한 번만 남긴다")
    fun sendQuizCard() {
        val card = message(20L, ChatMessageType.QUIZ_CARD, "{}")
        val text = message(21L, ChatMessageType.TEXT, "왜요?")
        every { quizCardService.send(2L, 1L, 301L, "왜요?") } returns QuizCardSendResult(listOf(card, text), rejectedAsDuplicate = false)
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        controller.sendMessage(1L, ChatSendRequest("왜요?", ChatMessageType.QUIZ_CARD, quizId = 301L), principal)

        verifyOrder {
            messagingTemplate.convertAndSend("/sub/chat/rooms/1", card)
            messagingTemplate.convertAndSend("/sub/chat/rooms/1", text)
        }
        verify(exactly = 1) { chatMessageNotifier.notifyNewMessage(any()) }
        verify { chatMessageNotifier.notifyNewMessage(card) }
        sentCount(ChatStompController.OUTCOME_SUCCESS) shouldBe 1.0
    }

    @Test
    @DisplayName("이미 올라온 질문이면 한마디만 보내고 보낸 사람에게 7007 프레임을 보낸다")
    fun sendDuplicateQuizCard() {
        val text = message(21L, ChatMessageType.TEXT, "저도요")
        every { quizCardService.send(2L, 1L, 301L, "저도요") } returns QuizCardSendResult(listOf(text), rejectedAsDuplicate = true)
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        controller.sendMessage(1L, ChatSendRequest("저도요", ChatMessageType.QUIZ_CARD, quizId = 301L), principal)

        verify { messagingTemplate.convertAndSend("/sub/chat/rooms/1", text) }
        verify {
            messagingTemplate.convertAndSend(
                "/sub/chat/rooms/1",
                match<Any> { it is QuizCardRejectedEvent && it.memberId == 2L && it.quizId == 301L && it.code == "7007" },
            )
        }
    }

    @Test
    @DisplayName("이미 올라온 질문에 한마디도 없으면 아무 메시지도 보내지 않고 알림도 남기지 않는다")
    fun sendDuplicateQuizCardWithoutComment() {
        every { quizCardService.send(2L, 1L, 301L, "") } returns QuizCardSendResult(emptyList(), rejectedAsDuplicate = true)
        val principal = UsernamePasswordAuthenticationToken(MemberPrincipal(2L), null, emptyList())

        controller.sendMessage(1L, ChatSendRequest("", ChatMessageType.QUIZ_CARD, quizId = 301L), principal)

        verify(exactly = 0) { chatMessageNotifier.notifyNewMessage(any()) }
    }
}

