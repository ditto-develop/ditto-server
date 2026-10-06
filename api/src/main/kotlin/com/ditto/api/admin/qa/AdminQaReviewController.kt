package com.ditto.api.admin.qa

import com.ditto.api.review.controller.MemberReviewController
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 더미의 평가 제출. 앱 평가 API를 더미로 불러 재매칭 성사·신청 알림까지 앱과 똑같이 일어난다. */
@Controller
class AdminQaReviewController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val qaRoomReviews: QaRoomReviews,
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

    /** 더미마다 남은 대상을 차례로 낸다. 한 대상이 거부되면 그 더미는 거기서 멈추고 다음 더미로 넘어간다. */
    @PostMapping("/admin/qa/rooms/{roomId}/reviews/submit-all-dummies")
    fun submitAllPending(
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "true") wantsRematch: Boolean,
        redirectAttributes: RedirectAttributes,
    ): String {
        val pendingReviewByAuthorId = qaRoomReviews.of(roomId)
            .filter { it.pendingTargets.isNotEmpty() }
            .associateBy { it.author.id }
        redirectAttributes.flashEachDummyAction(
            "더미 남은 평가 모두 내기",
            pendingReviewByAuthorId.values.map { it.author },
        ) { dummy ->
            val review = pendingReviewByAuthorId.getValue(dummy.id)
            val request = QaBulkReviewAnswer.request(review.canRematch, wantsRematch)
            review.pendingTargets.forEach { target ->
                memberReviewController.submitAnswer(qaDummies.principalOf(dummy.id), review.reviewId, target.member.id, request)
            }
        }
        return QaRoutes.roomReviews(roomId)
    }
}
