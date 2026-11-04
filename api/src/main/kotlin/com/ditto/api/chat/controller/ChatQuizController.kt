package com.ditto.api.chat.controller

import com.ditto.api.chat.dto.QuizCompareResponse
import com.ditto.api.chat.dto.QuizDistributionResponse
import com.ditto.api.chat.quiz.ChatQuizAnswerService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class ChatQuizController(
    private val chatQuizAnswerService: ChatQuizAnswerService,
) {
    /** 1:1·재매칭 방의 문항별 답 비교. 그룹 방은 `/quiz-distribution`을 쓴다. */
    @GetMapping("/api/v1/chat/rooms/{roomId}/quiz-compare")
    fun compare(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
    ): ApiResponse<QuizCompareResponse> =
        ApiResponse.ok(chatQuizAnswerService.compare(principal.memberId, roomId))

    /** 그룹 방의 문항별 답 분포(인원 수만). */
    @GetMapping("/api/v1/chat/rooms/{roomId}/quiz-distribution")
    fun distribution(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable roomId: Long,
    ): ApiResponse<QuizDistributionResponse> =
        ApiResponse.ok(chatQuizAnswerService.distribution(principal.memberId, roomId))
}
