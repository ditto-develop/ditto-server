package com.ditto.api.quiz.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.QuizProgressResponse
import com.ditto.api.quiz.dto.QuizSetWithProgressResponse
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
        quizProgressService.submitAnswer(principal.memberId, request, LocalDateTime.now())
        return ApiResponse(success = true)
    }

    @GetMapping("/api/v1/quiz-progress/current")
    fun getProgress(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<QuizProgressResponse> {
        return ApiResponse.ok(quizProgressService.getProgress(principal.memberId, LocalDateTime.now()))
    }

    @PostMapping("/api/v1/quiz-progress/reset")
    fun resetProgress(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<Unit> {
        quizProgressService.resetProgress(principal.memberId, LocalDateTime.now())
        return ApiResponse(success = true)
    }

    @GetMapping("/api/v1/quiz-progress/quiz-sets/{id}")
    fun getQuizSetWithProgress(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<QuizSetWithProgressResponse> {
        return ApiResponse.ok(quizProgressService.getQuizSetWithProgress(principal.memberId, id, LocalDateTime.now()))
    }
}
