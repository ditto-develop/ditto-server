package com.ditto.api.chat.websocket

import com.ditto.api.config.auth.ApiKeyProperties
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication

class StompAuthChannelInterceptorTest {

    private val apiKeyProperties = ApiKeyProperties(apiKey = "test-key")
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val chatRoomMemberRepository = mockk<ChatRoomMemberRepository>()
    private val interceptor = StompAuthChannelInterceptor(apiKeyProperties, jwtTokenProvider, chatRoomMemberRepository)
    private val channel = mockk<MessageChannel>()

    private fun connectMessage(apiKey: String?, authorization: String?): Message<*> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        accessor.setLeaveMutable(true)
        apiKey?.let { accessor.setNativeHeader("X-API-Key", it) }
        authorization?.let { accessor.setNativeHeader("Authorization", it) }
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun subscribeMessage(destination: String?, memberId: Long?): Message<*> {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.setLeaveMutable(true)
        destination?.let { accessor.destination = it }
        memberId?.let { accessor.user = UsernamePasswordAuthenticationToken(MemberPrincipal(it), null, emptyList()) }
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun sendFrame(destination: String, memberId: Long = 1L): Message<*> {
        val accessor = StompHeaderAccessor.create(StompCommand.SEND)
        accessor.setLeaveMutable(true)
        accessor.destination = destination
        accessor.user = UsernamePasswordAuthenticationToken(MemberPrincipal(memberId), null, emptyList())
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    @DisplayName("CONNECT: 유효한 API Key + JWT 면 principal 이 세팅된다")
    fun connectSuccess() {
        every { jwtTokenProvider.isValid("valid") } returns true
        every { jwtTokenProvider.getMemberId("valid") } returns 7L
        val message = connectMessage(apiKey = "test-key", authorization = "Bearer valid")

        interceptor.preSend(message, channel)

        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)!!
        val principal = (accessor.user as Authentication).principal as MemberPrincipal
        principal.memberId shouldBe 7L
    }

    @Test
    @DisplayName("CONNECT: API Key 가 틀리면 UNAUTHORIZED_ERROR 로 거부한다")
    fun connectWrongApiKey() {
        val message = connectMessage(apiKey = "wrong", authorization = "Bearer valid")

        shouldThrow<WarnException> {
            interceptor.preSend(message, channel)
        }.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
    }

    @Test
    @DisplayName("CONNECT: JWT 가 유효하지 않으면 UNAUTHORIZED_ERROR 로 거부한다")
    fun connectInvalidToken() {
        every { jwtTokenProvider.isValid(any()) } returns false
        val message = connectMessage(apiKey = "test-key", authorization = "Bearer bad")

        shouldThrow<WarnException> {
            interceptor.preSend(message, channel)
        }.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
    }

    @Test
    @DisplayName("SUBSCRIBE: 방 멤버면 통과한다")
    fun subscribeMember() {
        every { chatRoomMemberRepository.existsByRoomIdAndMemberId(5L, 1L) } returns true
        val message = subscribeMessage(destination = "/sub/chat/rooms/5", memberId = 1L)

        interceptor.preSend(message, channel) // 예외 없이 통과
    }

    @Test
    @DisplayName("SUBSCRIBE: 방 멤버가 아니면 NOT_CHAT_ROOM_MEMBER 로 거부한다")
    fun subscribeNonMember() {
        every { chatRoomMemberRepository.existsByRoomIdAndMemberId(5L, 1L) } returns false
        val message = subscribeMessage(destination = "/sub/chat/rooms/5", memberId = 1L)

        shouldThrow<WarnException> {
            interceptor.preSend(message, channel)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    @Test
    @DisplayName("SUBSCRIBE: 방 토픽이 아닌 destination 은 FORBIDDEN 으로 거부한다")
    fun subscribeInvalidDestination() {
        val message = subscribeMessage(destination = "/sub/other", memberId = 1L)

        shouldThrow<WarnException> {
            interceptor.preSend(message, channel)
        }.errorCode shouldBe ErrorCode.FORBIDDEN
    }

    @Test
    @DisplayName("SEND: 애플리케이션 목적지(/pub/**)로는 통과한다")
    fun sendToApp() {
        val message = sendFrame(destination = "/pub/chat/rooms/1")

        interceptor.preSend(message, channel) // 예외 없이 통과
    }

    @Test
    @DisplayName("SEND: 브로커 목적지(/sub/**)로 직접 보내면 FORBIDDEN — 컨트롤러/멤버십 우회 차단")
    fun sendToBrokerDestinationBlocked() {
        val message = sendFrame(destination = "/sub/chat/rooms/1")

        shouldThrow<WarnException> {
            interceptor.preSend(message, channel)
        }.errorCode shouldBe ErrorCode.FORBIDDEN
    }
}
