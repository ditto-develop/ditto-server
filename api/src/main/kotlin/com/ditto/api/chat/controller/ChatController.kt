package com.ditto.api.chat.controller

import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatReadRequest
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.api.chat.service.ChatService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.response.ApiResponse
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
}
