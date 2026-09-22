package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.dto.PersonalMatchResponse
import com.ditto.api.match.service.PersonalMatchService
import com.ditto.api.notification.notifier.PersonalMatchNotifier
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class PersonalMatchController(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchNotifier: PersonalMatchNotifier,
) {

    @PostMapping("/api/v1/matches/request")
    fun requestMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: PersonalMatchRequest,
    ): ApiResponse<PersonalMatchResponse> =
        ApiResponse.ok(personalMatchService.requestMatch(principal.memberId, request))

    @PostMapping("/api/v1/matches/request/{id}/accept")
    fun acceptMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<PersonalMatchResponse> =
        ApiResponse.ok(personalMatchService.acceptMatch(principal.memberId, id))

    /**
     * 신청 거절. 알림은 서비스 커밋 뒤 여기서 남긴다 — 트랜잭션 안에 외부 I/O(푸시)를 넣지 않는
     * 기존 규칙이고, 투표 마감([com.ditto.api.chat.controller.ChatVoteController])이 같은 구조다.
     * 거절은 이미 커밋됐으므로 알림 실패가 응답을 실패로 만들지 않는다(notifier 가 삼킨다).
     */
    @PostMapping("/api/v1/matches/request/{id}/reject")
    fun rejectMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<PersonalMatchResponse> {
        val rejected = personalMatchService.rejectMatch(principal.memberId, id)
        personalMatchNotifier.notifyRejected(
            matchId = rejected.id,
            requesterId = rejected.requesterId,
            rejectedBy = principal.memberId,
        )
        return ApiResponse.ok(rejected)
    }
}
