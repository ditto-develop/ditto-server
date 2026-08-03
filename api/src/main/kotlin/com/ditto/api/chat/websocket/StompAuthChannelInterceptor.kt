package com.ditto.api.chat.websocket

import com.ditto.api.config.auth.ApiKeyProperties
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * STOMP 프레임 레벨 인증·인가. `/ws` 핸드셰이크는 HTTP 계층에서 permitAll 이므로
 * (브라우저 WebSocket 은 커스텀 헤더를 못 실음) 실제 인증은 여기서 한다. 배경: ADR 0009.
 * - CONNECT: X-API-Key + Bearer JWT 검증 → MemberPrincipal 세팅. 실패 시 연결 거부.
 * - SUBSCRIBE: `/sub/chat/rooms/{id}` 방 멤버십 인가 (남의 방 구독=도청 차단) + 종료된 방 구독 차단.
 * - SEND: 앱 목적지(/pub 하위)로만 허용 — /sub 하위로 직접 SEND 시 컨트롤러·멤버십 우회(위조 주입) 차단.
 */
@Component
class StompAuthChannelInterceptor(
    private val apiKeyProperties: ApiKeyProperties,
    private val jwtTokenProvider: JwtTokenProvider,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> authenticate(accessor)
            StompCommand.SUBSCRIBE -> authorizeSubscribe(accessor)
            StompCommand.SEND -> authorizeSend(accessor)
            else -> Unit
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val apiKey = accessor.getFirstNativeHeader(API_KEY_HEADER)
        if (apiKey == null || apiKey != apiKeyProperties.apiKey) {
            throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        }

        val token = resolveToken(accessor)
        if (token == null || !jwtTokenProvider.isValid(token)) {
            throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        }

        val memberId = jwtTokenProvider.getMemberId(token)
        accessor.user = UsernamePasswordAuthenticationToken(MemberPrincipal(memberId), null, emptyList())
    }

    private fun authorizeSubscribe(accessor: StompHeaderAccessor) {
        val roomId = ChatStompDestinations.parseRoomId(accessor.destination)
            ?: throw WarnException(ErrorCode.FORBIDDEN)
        val memberId = currentMemberId(accessor)
            ?: throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)

        if (!chatRoomMemberRepository.existsByRoomIdAndMemberId(roomId, memberId)) {
            throw WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER)
        }
        // 끝난 방에는 새 메시지가 오지 않으므로 구독을 붙들고 있을 이유가 없다. 지난 대화는 REST 조회로 읽는다.
        val room = chatRoomRepository.findById(roomId).orElseThrow { WarnException(ErrorCode.CHAT_ROOM_NOT_FOUND) }
        if (room.isEnded) {
            throw WarnException(ErrorCode.CHAT_ROOM_ENDED)
        }
    }

    /**
     * SEND 는 반드시 애플리케이션 목적지(/pub 하위)로만 허용한다.
     * SimpleBroker 는 브로커 목적지(/sub 하위)로 온 SEND 도 구독자에게 그대로 방송하므로,
     * 이를 막지 않으면 인증 회원이 `SEND /sub/chat/rooms/{id}` 로 컨트롤러(@MessageMapping)와
     * 멤버십 검증·DB 저장을 우회해 위조 메시지를 주입할 수 있다.
     */
    private fun authorizeSend(accessor: StompHeaderAccessor) {
        val destination = accessor.destination
        if (destination == null || !destination.startsWith("${ChatStompDestinations.APP_PREFIX}/")) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }
    }

    private fun resolveToken(accessor: StompHeaderAccessor): String? =
        accessor.getFirstNativeHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.substring(BEARER_PREFIX.length)

    private fun currentMemberId(accessor: StompHeaderAccessor): Long? =
        (accessor.user as? Authentication)?.principal
            ?.let { it as? MemberPrincipal }
            ?.memberId

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
