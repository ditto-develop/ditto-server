package com.ditto.api.quiz.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class QuizProgressController(
    private val quizProgressService: QuizProgressService,
) {
    @PostMapping("/api/v1/quiz-progress/answers")
    fun submitAnswer(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: SubmitAnswerRequest,
    ): ApiResponse<Unit> {
        quizProgressService.submitAnswer(principal, request, LocalDateTime.now())
        return ApiResponse(success = true)
    }
}
