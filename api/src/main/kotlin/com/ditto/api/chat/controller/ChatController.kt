package com.ditto.api.chat.controller

import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatReadRequest
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.chat.service.ChatService
import com.ditto.api.chat.websocket.ChatStompDestinations
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.review.service.EndedChatReviewOpener
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import java.time.LocalDateTime
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ChatController(
    private val chatService: ChatService,
    private val chatRoomEndService: ChatRoomEndService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val endedChatReviewOpener: EndedChatReviewOpener,
) {

    @GetMapping("/api/v1/chat/rooms")
    fun getMyRooms(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<List<ChatRoomResponse>> =
        ApiResponse.ok(chatService.getMyRooms(principal.memberId))

    @GetMapping("/api/v1/chat/rooms/{roomId}/messages")
    fun getMessages(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(required = false, defaultValue = "30") size: Int,
    ): ApiResponse<ChatMessagesResponse> =
        ApiResponse.ok(chatService.getMessages(principal.memberId, roomId, cursor, size))

    @PostMapping("/api/v1/chat/rooms/{roomId}/read")
    fun read(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @RequestBody request: ChatReadRequest,
    ): ApiResponse<Unit> {
        chatService.markAsRead(principal.memberId, roomId, request.lastReadMessageId)
        return ApiResponse.ok(Unit)
    }

    /**
     * 1:1 채팅 종료. 이미 끝난 방에 다시 요청해도 성공으로 답한다(더블 탭·재시도 대비).
     *
     * 이번 요청이 실제로 끝냈을 때만 종료 SYSTEM 메시지를 발행하고 평가를 연다 — 상대 화면이
     * 그때 바로 "상대방이 채팅을 종료했습니다"로 바뀌어야 하는데, 종료된 방은 재구독이 막혀
     * 나중에 따라잡을 수 없기 때문이다. 둘 다 서비스 트랜잭션이 커밋된 뒤에 일어난다.
     *
     * 평가 열기가 실패해도 이 요청은 성공으로 답한다 — 종료는 이미 확정됐고, 놓친 평가는
     * 스케줄러의 누락 복구가 줍는다(계획서 ⑤-1의 at-least-once 계약).
     */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/end")
    fun end(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
    ): ApiResponse<Unit> {
        chatRoomEndService.endByUser(roomId, principal.memberId, LocalDateTime.now())
            ?.let {
                messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), it)
                endedChatReviewOpener.openFor(listOf(roomId))
            }
        return ApiResponse.ok(Unit)
    }

    /** 이미지 전송용 presigned PUT URL 발급 (방 멤버만). 업로드 후 messageType=IMAGE, content=objectKey 로 전송. */
    @PostMapping("/api/v1/chat/rooms/{roomId}/image-upload-urls")
    fun issueImageUploadUrls(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ChatImageUploadUrlsRequest,
    ): ApiResponse<ChatImageUploadUrlsResponse> =
        ApiResponse.ok(chatService.issueImageUploadUrls(principal.memberId, roomId, request))
}
