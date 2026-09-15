package com.ditto.api.match.controller

import com.ditto.api.match.dto.MatchingRegenerateResponse
import com.ditto.api.match.service.MatchmakingService
import com.ditto.common.response.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 매칭 운영용(admin) API.
 *
 * admin 경로(`/api/v1/admin` 하위 전체)는 JwtAuthenticationFilter에서 role=ADMIN 회원만 접근할 수 있다.
 */
@RestController
class MatchAdminController(
    private val matchmakingService: MatchmakingService,
) {

    /**
     * 특정 퀴즈셋의 매칭 후보를 재생성한다. 기존 후보를 모두 삭제하고 다시 계산한다(멱등).
     * 스케줄러와 달리 마감·후보 존재 여부와 무관하게 실행한다.
     * 응답이 시작된 그룹 퀴즈셋은 `MATCH_CANDIDATES_ALREADY_RESPONDED`로 실패한다 — 조용히 건너뛰지 않는다.
     */
    @PostMapping("/api/v1/admin/quiz-sets/{quizSetId}/matching/regenerate")
    fun regenerateMatching(@PathVariable quizSetId: Long): ApiResponse<MatchingRegenerateResponse> =
        ApiResponse.ok(MatchingRegenerateResponse.from(matchmakingService.generateMatchingCandidates(quizSetId)))
}
