package com.ditto.api.review.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.review.dto.MemberReviewResponse
import com.ditto.api.review.service.MemberReviewService
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberReviewController(
    private val memberReviewService: MemberReviewService,
) {

    @GetMapping("/api/v1/member-reviews")
    fun getMyPendingReviews(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<List<MemberReviewResponse>> =
        ApiResponse.ok(memberReviewService.getMyPendingReviews(principal.memberId))
}
