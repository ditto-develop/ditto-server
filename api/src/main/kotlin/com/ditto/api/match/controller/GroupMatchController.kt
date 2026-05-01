package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.dto.GroupMatchJoinResponse
import com.ditto.api.match.service.GroupMatchService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GroupMatchController(
    private val groupMatchService: GroupMatchService,
) {

    @PostMapping("/api/v1/matches/group/join")
    fun joinGroupMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: GroupMatchJoinRequest,
    ): ApiResponse<GroupMatchJoinResponse> =
        ApiResponse.ok(groupMatchService.joinGroupMatch(principal.memberId, request))

    @PostMapping("/api/v1/matches/group/decline")
    fun declineGroupMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: GroupMatchDeclineRequest,
    ): ApiResponse<Unit> {
        groupMatchService.declineGroupMatch(principal.memberId, request)
        return ApiResponse(success = true)
    }
}
