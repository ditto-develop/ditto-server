package com.ditto.api.chat.websocket

import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.service.ChatService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import java.security.Principal

@Controller
class ChatStompController(
    private val chatService: ChatService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val chatMessageNotifier: ChatMessageNotifier,
) {

    /**
     * 클라이언트가 `/pub/chat/rooms/{roomId}` 로 전송 → 저장 후 `/sub/chat/rooms/{roomId}` 로 브로드캐스트.
     *
     * 알림은 **브로드캐스트 뒤에** 남긴다 — 실시간 전달이 알림 적재를 기다리지 않아야 하고,
     * 적재가 실패해도 메시지는 이미 전달돼 있어야 한다(알림 실패는 `ChatMessageNotifier`가 삼킨다).
     */
    @MessageMapping(ChatStompDestinations.SEND_MAPPING)
    fun sendMessage(
        @DestinationVariable roomId: Long,
        @Payload request: ChatSendRequest,
        principal: Principal,
    ) {
        val memberId = memberIdOf(principal)
        val message = chatService.sendMessage(memberId, roomId, request.content, request.messageType)
        messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), message)
        chatMessageNotifier.notifyNewMessage(message)
    }

    private fun memberIdOf(principal: Principal): Long {
        val authentication = principal as? Authentication ?: throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        val memberPrincipal = authentication.principal as? MemberPrincipal
            ?: throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        return memberPrincipal.memberId
    }
}
