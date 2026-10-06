package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaReview
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
        redirectAttributes.flashDummyActionWithResult(qaMemberLabels.one(dummyId), "${target.label} 평가 내기") {
            submit(dummyId, reviewId, targetId, request)
        }
        return QaRoutes.roomReviewsSection(roomId)
    }

    @PostMapping("/admin/qa/rooms/{roomId}/reviews/submit-all-dummies")
    fun submitPendingForAllDummies(
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "REAL_MEMBERS_ONLY") rematchChoice: QaBulkRematchChoice,
        redirectAttributes: RedirectAttributes,
    ): String {
        val pendingReviewByAuthorId = qaRoomReviews.findDummyReviews(roomId)
            .filter { it.hasPendingTargets }
            .associateBy { it.author.id }
        redirectAttributes.flashEachDummyAction(
            "방 #$roomId 더미 남은 평가 모두 내기",
            pendingReviewByAuthorId.values.map { it.author },
        ) { dummy -> submitPendingTargets(pendingReviewByAuthorId.getValue(dummy.id), rematchChoice) }
        return QaRoutes.roomReviewsSection(roomId)
    }

    /** 대상 하나가 거부되면 거기서 멈춘다. 앞 대상은 이미 확정됐으니 몇 명까지 냈는지 거부 문구에 붙인다. */
    private fun submitPendingTargets(review: QaReview, rematchChoice: QaBulkRematchChoice) {
        review.pendingTargets.forEachIndexed { submittedCount, target ->
            val request = QaBulkReviewAnswer.request(
                canRematch = review.canRematch,
                wantsRematch = rematchChoice.wantsToward(target.isDummy),
            )
            runCatching { submit(review.author.id, review.reviewId, target.member.id, request) }
                .onFailure { rejection ->
                    if (rejection !is WarnException) throw rejection
                    val progress = "${review.pendingTargets.size}명 중 ${submittedCount}명 낸 뒤 ${target.member.label}에서 멈춤"
                    throw WarnException(rejection.errorCode, "$progress: ${rejection.message}")
                }
        }
    }

    /** 이번 제출로 재매칭이 성사되면 그 사실을 돌려준다. */
    private fun submit(dummyId: Long, reviewId: Long, targetId: Long, request: ReviewAnswerSubmitRequest): String? {
        val response = memberReviewController.submitAnswer(qaDummies.principalOf(dummyId), reviewId, targetId, request)
        return response.data?.rematch?.let { "재매칭 성사" }
    }
}
