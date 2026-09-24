package com.ditto.api.review.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.review.dto.MemberReviewResponse
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.api.review.dto.ReviewAnswerSubmitResponse
import com.ditto.api.notification.notifier.RematchNotifier
import com.ditto.api.review.service.MemberReviewService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MemberReviewController(
    private val memberReviewService: MemberReviewService,
    private val rematchNotifier: RematchNotifier,
) {

    @Loggable
    @GetMapping("/api/v1/member-reviews")
    fun getMyPendingReviews(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<List<MemberReviewResponse>> =
        ApiResponse.ok(memberReviewService.getMyPendingReviews(principal.memberId))

    /**
     * 대상 한 명에 대한 최종 제출. 마지막 대상을 제출하면 평가가 자동 완료되므로 별도 완료 API는 없다.
     * 재매칭 신청·거절 알림은 서비스 커밋 뒤 여기서 남긴다(요청 경로 규칙, ADR 0018). 의사가 실린 제출(그룹)만.
     */
    @Loggable
    @PutMapping("/api/v1/member-reviews/{reviewId}/targets/{memberId}")
    fun submitAnswer(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable reviewId: Long,
        @PathVariable("memberId") reviewedMemberId: Long,
        @RequestBody request: ReviewAnswerSubmitRequest,
    ): ApiResponse<ReviewAnswerSubmitResponse> {
        val response = memberReviewService.submitAnswer(
            memberId = principal.memberId,
            reviewId = reviewId,
            reviewedMemberId = reviewedMemberId,
            request = request,
        )
        if (request.wantsOneToOneRematch != null) {
            rematchNotifier.notifySubmitted(reviewId, submitterId = principal.memberId, counterpartId = reviewedMemberId)
        }
        return ApiResponse.ok(response)
    }
}
