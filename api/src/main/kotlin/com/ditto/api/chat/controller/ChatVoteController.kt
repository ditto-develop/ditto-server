package com.ditto.api.chat.controller

import com.ditto.api.chat.dto.ChatVoteCreateRequest
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 그룹 만남 투표. 계약 전반(생성·cast·close 응답이 전부 상세 한 형태인 이유, 승자·비율을
 * 서버가 계산하지 않는 이유)은 [ChatVoteService]와 `docs/plans/group-vote.md` 참고.
 */
@RestController
class ChatVoteController(
    private val chatVoteService: ChatVoteService,
) {

    /** 투표 생성 — 방당 열린 투표 1개. 이미 있으면 8202 로 거부한다(멱등 아님). */
    @Loggable
    @PostMapping("/api/v1/chat/rooms/{roomId}/votes")
    fun createVote(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: ChatVoteCreateRequest,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.createVote(roomId, principal.memberId, request))

    /** 방의 투표 목록(최신순) — 재접속·브로드캐스트 유실 시 배너 복구 경로. */
    @GetMapping("/api/v1/chat/rooms/{roomId}/votes")
    fun getVotes(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
    ): ApiResponse<List<ChatVoteDetailResponse>> =
        ApiResponse.ok(chatVoteService.getVotes(roomId, principal.memberId))

    /** 투표 상세 — 집계 + 내 표. */
    @GetMapping("/api/v1/chat/rooms/{roomId}/votes/{voteId}")
    fun getVote(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
        @PathVariable voteId: Long,
    ): ApiResponse<ChatVoteDetailResponse> =
        ApiResponse.ok(chatVoteService.getVote(roomId, voteId, principal.memberId))
}
