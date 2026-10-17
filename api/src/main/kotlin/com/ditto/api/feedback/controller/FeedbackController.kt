package com.ditto.api.feedback.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.dto.CreateFeedbackResponse
import com.ditto.api.feedback.service.FeedbackService
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class FeedbackController(
    private val feedbackService: FeedbackService,
) {

    @PostMapping("/api/v1/feedbacks")
    fun createFeedback(
        @Valid @RequestBody request: CreateFeedbackRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<CreateFeedbackResponse> {
        val result = feedbackService.submitFeedback(principal.memberId, request)
        return ApiResponse.ok(result)
    }
}
