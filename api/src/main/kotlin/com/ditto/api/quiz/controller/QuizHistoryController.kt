package com.ditto.api.quiz.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.QuizHistoryDetailResponse
import com.ditto.api.quiz.dto.QuizHistoryResponse
import com.ditto.api.quiz.service.QuizHistoryService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class QuizHistoryController(
    private val quizHistoryService: QuizHistoryService,
    private val serverTimeProvider: ServerTimeProvider,
) {
    @GetMapping("/api/v1/quiz-history")
    fun getHistory(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<QuizHistoryResponse> =
        ApiResponse.ok(quizHistoryService.getHistory(principal.memberId, serverTimeProvider.now()))

    @GetMapping("/api/v1/quiz-history/{quizSetId}")
    fun getDetail(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable quizSetId: Long,
    ): ApiResponse<QuizHistoryDetailResponse> =
        ApiResponse.ok(quizHistoryService.getDetail(principal.memberId, quizSetId, serverTimeProvider.now()))
}
