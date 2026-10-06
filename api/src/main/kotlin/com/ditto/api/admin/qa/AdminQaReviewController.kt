package com.ditto.api.admin.qa

import com.ditto.api.review.controller.MemberReviewController
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 더미의 평가 제출. 앱 평가 API를 더미로 불러 재매칭 성사·신청 알림까지 앱과 똑같이 일어난다. */
@Controller
class AdminQaReviewController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val memberReviewController: MemberReviewController,
) {
    @PostMapping("/admin/qa/dummies/{dummyId}/rooms/{roomId}/reviews/{reviewId}/targets/{targetId}")
    fun submit(
        @PathVariable dummyId: Long,
        @PathVariable roomId: Long,
        @PathVariable reviewId: Long,
        @PathVariable targetId: Long,
        @ModelAttribute request: ReviewAnswerSubmitRequest,
        redirectAttributes: RedirectAttributes,
    ): String {
        val target = qaMemberLabels.one(targetId)
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "${target.nickname} 평가 내기") {
            memberReviewController.submitAnswer(qaDummies.principalOf(dummyId), reviewId, targetId, request)
        }
        return QaRoutes.roomReviews(roomId)
    }
}
