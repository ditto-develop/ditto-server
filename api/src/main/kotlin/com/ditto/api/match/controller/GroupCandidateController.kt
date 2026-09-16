package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.GroupCandidateResponse
import com.ditto.api.match.service.GroupCandidateService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class GroupCandidateController(
    private val groupCandidateService: GroupCandidateService,
) {

    /** 회원이 최근 완료한 그룹 퀴즈셋의 후보 그룹 목록 조회. 대상 퀴즈셋은 서버가 결정한다. */
    @GetMapping("/api/v1/matches/group")
    fun getGroupCandidates(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<GroupCandidateResponse> =
        ApiResponse.ok(groupCandidateService.getGroupCandidates(principal.memberId))
}
