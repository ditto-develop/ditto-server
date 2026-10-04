package com.ditto.api.admin.qa

import com.ditto.api.match.controller.GroupMatchController
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 더미의 그룹 초대 수락·거절. */
@Controller
class AdminQaGroupController(
    private val adminQaService: AdminQaService,
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val groupMatchController: GroupMatchController,
) {
    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/accept")
    fun accept(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "그룹 #$groupMatchId 수락") {
            groupMatchController.accept(qaDummies.principalOf(dummyId), groupMatchId)
        }
        return QaRoutes.GROUP_SECTION
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/decline")
    fun decline(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashDummyAction(qaMemberLabels.one(dummyId), "그룹 #$groupMatchId 거절") {
            groupMatchController.decline(qaDummies.principalOf(dummyId), groupMatchId)
        }
        return QaRoutes.GROUP_SECTION
    }

    @PostMapping("/admin/qa/group-matches/{groupMatchId}/accept-pending-dummies")
    fun acceptForPendingDummies(
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.flashEachDummyAction(
            "그룹 #$groupMatchId 대기 중인 더미 수락",
            adminQaService.findPendingDummiesIn(groupMatchId),
        ) { dummy ->
            groupMatchController.accept(qaDummies.principalOf(dummy.id), groupMatchId)
        }
        return QaRoutes.GROUP_SECTION
    }
}
