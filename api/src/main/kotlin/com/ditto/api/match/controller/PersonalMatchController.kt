package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.PersonalMatchListResponse
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.dto.PersonalMatchResponse
import com.ditto.api.match.service.PersonalMatchService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PersonalMatchController(
    private val personalMatchService: PersonalMatchService,
) {

    @GetMapping("/api/v1/matches/1on1")
    fun getPersonalMatches(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestParam quizSetId: Long,
    ): ApiResponse<PersonalMatchListResponse> =
        ApiResponse.ok(personalMatchService.getPersonalMatches(principal.memberId, quizSetId))

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

    @PostMapping("/api/v1/matches/request/{id}/reject")
    fun rejectMatch(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<PersonalMatchResponse> =
        ApiResponse.ok(personalMatchService.rejectMatch(principal.memberId, id))
}
