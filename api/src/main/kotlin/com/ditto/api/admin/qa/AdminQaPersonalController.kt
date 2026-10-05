package com.ditto.api.admin.qa

import com.ditto.api.match.controller.PersonalMatchController
import com.ditto.api.match.dto.PersonalMatchRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 더미의 1:1 신청·수락·거절. */
@Controller
class AdminQaPersonalController(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val personalMatchController: PersonalMatchController,
) {
    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches")
    fun request(
        @PathVariable dummyId: Long,
        @RequestParam receiverId: Long,
        @RequestParam quizSetId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        val receiver = qaMemberLabels.one(receiverId)
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "${receiver.label}에게 1:1 신청") {
            personalMatchController.requestMatch(
                qaDummies.principalOf(dummyId),
                PersonalMatchRequest(receiverId = receiverId, quizSetId = quizSetId),
            )
        }
        return QaRoutes.PERSONAL_SECTION
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches/{matchId}/accept")
    fun accept(
        @PathVariable dummyId: Long,
        @PathVariable matchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "1:1 신청 #$matchId 수락") {
            personalMatchController.acceptMatch(qaDummies.principalOf(dummyId), matchId)
        }
        return QaRoutes.PERSONAL_SECTION
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches/{matchId}/reject")
    fun reject(
        @PathVariable dummyId: Long,
        @PathVariable matchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "1:1 신청 #$matchId 거절") {
            personalMatchController.rejectMatch(qaDummies.principalOf(dummyId), matchId)
        }
        return QaRoutes.PERSONAL_SECTION
    }
}
