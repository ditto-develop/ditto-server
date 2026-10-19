package com.ditto.api.chat.websocket

import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.service.ChatService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
    private val meterRegistry: MeterRegistry,
) {

    init {
        // 결과별로 0부터 만들어 둔다. 처음 생긴 시계열의 첫 1은 increase()가 못 잡아서 첫 실패 알림이 빠진다.
        listOf(OUTCOME_SUCCESS, OUTCOME_REJECTED, OUTCOME_ERROR).forEach { sendCounter(it) }
    }

    /**
     * 클라이언트가 `/pub/chat/rooms/{roomId}` 로 전송 → 저장 후 `/sub/chat/rooms/{roomId}` 로 브로드캐스트.
     *
     * 알림은 **브로드캐스트 뒤에** 남긴다 — 실시간 전달이 알림 적재를 기다리지 않아야 하고,
     * 적재가 실패해도 메시지는 이미 전달돼 있어야 한다(알림 실패는 `ChatMessageNotifier`가 삼킨다).
     *
     * STOMP 전송은 HTTP 메트릭에 안 잡혀서 결과를 따로 센다 — 실패율이 채팅 장애 알림의 기준이다.
     * 저장은 됐는데 브로드캐스트가 실패해도 error로 센다 — 상대에게 실시간으로 안 갔으니 사용자 입장에선 실패다.
     */
    @MessageMapping(ChatStompDestinations.SEND_MAPPING)
    fun sendMessage(
        @DestinationVariable roomId: Long,
        @Payload request: ChatSendRequest,
        principal: Principal,
    ) {
        val message = try {
            val memberId = memberIdOf(principal)
            chatService.sendMessage(memberId, roomId, request.content, request.messageType)
                .also { messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), it) }
        } catch (e: WarnException) {
            countSend(OUTCOME_REJECTED)
            throw e
        } catch (e: Exception) {
            countSend(OUTCOME_ERROR)
            throw e
        }
        countSend(OUTCOME_SUCCESS)
        chatMessageNotifier.notifyNewMessage(message)
    }

    private fun countSend(outcome: String) = sendCounter(outcome).increment()

    private fun sendCounter(outcome: String): Counter =
        Counter.builder(SEND_METRIC)
            .description("STOMP 채팅 메시지 전송 결과")
            .tag("outcome", outcome)
            .register(meterRegistry)

    private fun memberIdOf(principal: Principal): Long {
        val authentication = principal as? Authentication ?: throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        val memberPrincipal = authentication.principal as? MemberPrincipal
            ?: throw WarnException(ErrorCode.UNAUTHORIZED_ERROR)
        return memberPrincipal.memberId
    }

    companion object {
        const val SEND_METRIC = "ditto.chat.messages.sent"
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_REJECTED = "rejected"
        const val OUTCOME_ERROR = "error"
    }
}
