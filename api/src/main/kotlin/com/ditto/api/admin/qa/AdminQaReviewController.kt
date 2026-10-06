package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaReview
import com.ditto.api.admin.qa.dto.QaReviewTarget
import com.ditto.api.review.controller.MemberReviewController
import com.ditto.api.review.dto.ReviewAnswerSubmitRequest
import com.ditto.common.exception.WarnException
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** QA 방 화면의 더미 평가 내기. */
@Controller
class AdminQaReviewController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val qaRoomReviews: QaRoomReviews,
    private val memberReviewController: MemberReviewController,
) {
    @PostMapping("/admin/qa/dummies/{dummyId}/rooms/{roomId}/reviews/{reviewId}/targets/{targetId}")
    fun submitAsDummy(
        @PathVariable dummyId: Long,
        @PathVariable roomId: Long,
        @PathVariable reviewId: Long,
        @PathVariable targetId: Long,
        @ModelAttribute request: ReviewAnswerSubmitRequest,
        redirectAttributes: RedirectAttributes,
    ): String {
        val target = qaMemberLabels.one(targetId)
        redirectAttributes.flashDummyActionWithResult(qaMemberLabels.one(dummyId), "${target.label} 평가") {
            submitAnswerAsDummy(dummyId, reviewId, targetId, request)?.label
        }
        return QaRoutes.roomReviewsSection(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/reviews/submit-all-dummies")
    fun submitPendingForAllDummies(
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "REAL_MEMBERS_ONLY") rematchChoice: QaBulkRematchChoice,
        redirectAttributes: RedirectAttributes,
    ): String {
        val pendingReviews = qaRoomReviews.findDummyReviews(roomId).filter { it.hasPendingTargets }
        redirectAttributes.flashEachDummyActionWithResults(
            "방 #$roomId 더미 모두 남은 평가 내기",
            pendingReviews.map { it.author },
        ) { dummy -> submitPendingTargets(pendingReviews.first { it.author.id == dummy.id }, rematchChoice) }
        return QaRoutes.roomReviewsSection(roomId)
    }

    /** 대상 하나가 거부되면 거기서 멈춘다. 앞 대상은 이미 확정됐으니 몇 명까지 냈는지 거부 문구에 붙인다. */
    private fun submitPendingTargets(review: QaReview, rematchChoice: QaBulkRematchChoice): List<String> =
        review.pendingTargets.mapIndexed { submittedCount, target ->
            val request = QaBulkReviewAnswer.request(
                canRematch = review.canRematch,
                wantsRematch = rematchChoice.wantsToward(target.isDummy),
            )
            runRejectable { submitAnswerAsDummy(review.author.id, review.reviewId, target.member.id, request) }
                .getOrElse { throw (it as WarnException).stoppedAt(target, submittedCount, review.pendingTargets.size) }
        }.mapNotNull { it?.label }

    private fun WarnException.stoppedAt(target: QaReviewTarget, submittedCount: Int, targetCount: Int): WarnException {
        val progress = if (submittedCount == 0) "" else "대상 ${targetCount}명 중 ${submittedCount}명까지 내고 "
        return WarnException(errorCode, "$progress${target.member.label}에서 멈춤: $message")
    }

    private fun submitAnswerAsDummy(
        dummyId: Long,
        reviewId: Long,
        targetId: Long,
        request: ReviewAnswerSubmitRequest,
    ): QaRematchOutcome? {
        memberReviewController.submitAnswer(qaDummies.principalOf(dummyId), reviewId, targetId, request)
        if (request.wantsOneToOneRematch == null) return null
        return qaRoomReviews.findRematchOutcome(reviewId, dummyId, targetId)
    }
}
