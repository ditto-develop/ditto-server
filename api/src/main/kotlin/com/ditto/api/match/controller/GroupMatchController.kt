package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.GroupMatchAcceptResponse
import com.ditto.api.match.service.GroupMatchService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GroupMatchController(
    private val groupMatchService: GroupMatchService,
) {

    /** 후보 그룹 초대 수락. 수락 인원이 최소 인원에 닿으면 성사되고 금요일에 채팅방이 열린다. */
    @PostMapping("/api/v1/matches/group/{groupMatchId}/accept")
    fun accept(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable groupMatchId: Long,
    ): ApiResponse<GroupMatchAcceptResponse> =
        ApiResponse.ok(groupMatchService.acceptGroupMatch(principal.memberId, groupMatchId))

    /** 후보 그룹 초대 거절. */
    @PostMapping("/api/v1/matches/group/{groupMatchId}/decline")
    fun decline(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable groupMatchId: Long,
    ): ApiResponse<Unit> {
        groupMatchService.declineGroupMatch(principal.memberId, groupMatchId)
        return ApiResponse.ok(Unit)
    }
}
