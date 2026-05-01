package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.MatchingStatusResponse
import com.ditto.api.match.service.MatchingStatusService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchingStatusController(
    private val matchingStatusService: MatchingStatusService,
) {

    @GetMapping("/api/v1/matching/status/{quizSetId}")
    fun getMatchingStatus(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable quizSetId: Long,
    ): ApiResponse<MatchingStatusResponse> =
        ApiResponse.ok(matchingStatusService.getMatchingStatus(principal.memberId, quizSetId))
}
