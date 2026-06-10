package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.MatchCandidateResponse
import com.ditto.api.match.service.MatchCandidateService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchCandidateController(
    private val matchCandidateService: MatchCandidateService,
) {

    /** 회원이 최근 완료한 1:1 퀴즈셋의 추천 후보 목록 조회. 대상 퀴즈셋은 서버가 결정한다. */
    @GetMapping("/api/v1/matches/1on1")
    fun getMatchCandidates(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MatchCandidateResponse> =
        ApiResponse.ok(matchCandidateService.getMatchCandidates(principal.memberId))
}
