package com.ditto.api.chat.controller

import com.ditto.api.chat.dto.ChatVoteCastRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.api.chat.dto.ChatVoteCreateRequest.PlaceOptionRequest
import com.ditto.api.chat.dto.ChatVoteCreateRequest.TimeOptionRequest
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.api.chat.websocket.ChatStompDestinations
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.notification.notifier.ChatVoteClosedNotifier
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * 그룹 만남 투표. 계약 전반(생성·cast·close 응답이 전부 상세 한 형태인 이유, 승자·비율을
 * 서버가 계산하지 않는 이유)은 [ChatVoteService]와 `docs/plans/group-vote.md` 참고.
 */
@RestController
class ChatVoteController(
    private val chatVoteService: ChatVoteService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val chatVoteClosedNotifier: ChatVoteClosedNotifier,
) {

    /**
     * 투표 생성 — 방당 열린 투표 1개. 이미 있으면 VOTE_ALREADY_EXISTS 로 거부한다(멱등 아님).
     * 생성 SYSTEM 메시지는 서비스 커밋 뒤 여기서 브로드캐스트한다 — 잠금 구간에 외부 I/O 를
     * 넣지 않는 규칙(ADR 0011)이고, 채팅 종료가 같은 구조다.
     */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes")
    fun createVote(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ChatVoteCreateRequest,
    ): ApiResponse<ChatVoteDetailResponse> {
        val result = chatVoteService.createVote(roomId, principal.memberId, request)
        result.systemMessage?.let {
            messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), it)
        }
        return ApiResponse.ok(result.detail)
    }

    /**
     * 투표 마감 — 권한은 방 멤버 누구나, 멱등이다(이미 닫힌 투표에 다시 요청해도 성공).
     * 마감 SYSTEM 메시지는 이번 요청이 실제로 닫았을 때만 브로드캐스트된다.
     */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}/close")
    fun close(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
    ): ApiResponse<ChatVoteDetailResponse> {
        val result = chatVoteService.close(roomId, voteId, principal.memberId, LocalDateTime.now())
        result.systemMessage?.let {
            messagingTemplate.convertAndSend(ChatStompDestinations.roomTopic(roomId), it)
            chatVoteClosedNotifier.notifyClosed(roomId, closedBy = principal.memberId)
        }
        return ApiResponse.ok(result.detail)
    }

    /** 방의 투표 목록(최신순) — 재접속·브로드캐스트 유실 시 배너 복구 경로. */
    @GetMapping("/api/v1/chat/rooms/{roomId}/votes")
    fun getVotes(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
    ): ApiResponse<List<ChatVoteDetailResponse>> =
        ApiResponse.ok(chatVoteService.getVotes(roomId, principal.memberId))

    /** 장소 선택지 추가 — 진행 중 투표에 하나씩. 방 멤버 누구나, 조용히(메시지 없음). */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}/place-options")
    fun addPlaceOption(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        @Valid @RequestBody request: PlaceOptionRequest,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.addPlaceOption(roomId, voteId, principal.memberId, request))

    /** 시간 선택지 추가 — 규칙은 장소 추가와 같다. */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}/time-options")
    fun addTimeOption(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        @Valid @RequestBody request: TimeOptionRequest,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.addTimeOption(roomId, voteId, principal.memberId, request))

    /** 투표하기 — 요청에 담긴 집합이 내 최종 선택이다(치환). 재투표도 같은 경로다. */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}/cast")
    fun cast(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
        @Valid @RequestBody request: ChatVoteCastRequest,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.cast(roomId, voteId, principal.memberId, request))

    /** 투표 상세 — 집계 + 내 표. */
    @GetMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}")
    fun getVote(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.getVote(roomId, voteId, principal.memberId))
}
