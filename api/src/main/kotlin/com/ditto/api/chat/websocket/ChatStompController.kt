package com.ditto.api.chat.websocket

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatSendRequest
import com.ditto.api.chat.dto.QuizCardRejectedEvent
import com.ditto.api.chat.quiz.QuizCardService
import com.ditto.api.chat.service.ChatService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.notifier.ChatMessageNotifier
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessageType
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
    private val quizCardService: QuizCardService,
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
     *
     * 질문 카드(`QUIZ_CARD`)는 카드와 덧붙인 한마디를 한 프레임으로 받아 서버가 둘의 순서를 정한다([QuizCardService]).
     * 이미 올라온 문항이면 카드는 버리고 보낸 사람에게 [QuizCardRejectedEvent]를 보낸다.
     */
    @MessageMapping(ChatStompDestinations.SEND_MAPPING)
    fun sendMessage(
        @DestinationVariable roomId: Long,
        @Payload request: ChatSendRequest,
        principal: Principal,
    ) {
        val messages = try {
            val memberId = memberIdOf(principal)
            if (request.messageType == ChatMessageType.QUIZ_CARD) {
                sendQuizCard(memberId, roomId, request)
            } else {
                listOf(chatService.sendMessage(memberId, roomId, request.content, request.messageType))
                    .onEach { messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), it) }
            }
        } catch (e: WarnException) {
            countSend(OUTCOME_REJECTED)
            throw e
        } catch (e: Exception) {
            countSend(OUTCOME_ERROR)
            throw e
        }
        countSend(OUTCOME_SUCCESS)
        // 카드와 한마디가 함께 왔으면 카드 한 번만 알린다. 같은 방 알림은 어차피 접히고, 두 번 울리면 거슬린다.
        messages.firstOrNull()?.let { chatMessageNotifier.notifyNewMessage(it) }
    }

    private fun sendQuizCard(memberId: Long, roomId: Long, request: ChatSendRequest): List<ChatMessageResponse> {
        val result = quizCardService.send(memberId, roomId, request.quizId, request.content)
        val topic = ChatStompDestinations.roomTopic(roomId)
        result.messages.forEach { messagingTemplate.convertAndSend(topic, it) }
        if (result.rejectedAsDuplicate) {
            // quizId 는 send 가 검증을 마쳐 null 이 아니다.
            messagingTemplate.convertAndSend(topic, QuizCardRejectedEvent(roomId, memberId, request.quizId!!))
        }
        return result.messages
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
