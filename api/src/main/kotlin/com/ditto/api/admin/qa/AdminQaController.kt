package com.ditto.api.admin.qa

import com.ditto.api.match.controller.GroupMatchController
import com.ditto.api.match.controller.PersonalMatchController
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.system.ServerTimeService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * QA 콘솔. 로그인할 수 없는 더미 회원을 대신 움직여 1:1·그룹 채팅을 혼자 확인한다.
 * 더미의 행동은 앱이 부르는 API 컨트롤러를 더미 principal 로 그대로 호출한다. 브로드캐스트·알림까지 실제 경로와 같아야 QA 결과를 믿을 수 있다.
 */
@Controller
class AdminQaController(
    private val adminQaService: AdminQaService,
    private val adminQaRoomService: AdminQaRoomService,
    private val qaDummies: QaDummies,
    private val serverTimeService: ServerTimeService,
    private val personalMatchController: PersonalMatchController,
    private val groupMatchController: GroupMatchController,
) {
    @GetMapping("/admin/qa")
    fun page(model: Model): String {
        model.addAttribute("console", adminQaService.getConsole())
        model.addAttribute("rooms", adminQaRoomService.getRoomSummaries())
        model.addAttribute("timeOverridden", serverTimeService.getOverride().enabled)
        model.addAttribute("active", "qa")
        return "qa/console"
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches")
    fun requestPersonalMatch(
        @PathVariable dummyId: Long,
        @RequestParam receiverId: Long,
        @RequestParam quizSetId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportDummyAction("더미 #$dummyId → 회원 #$receiverId 1:1 신청") {
            personalMatchController.requestMatch(
                qaDummies.principalOf(dummyId),
                PersonalMatchRequest(receiverId = receiverId, quizSetId = quizSetId),
            )
        }
        return PERSONAL_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches/{matchId}/accept")
    fun acceptPersonalMatch(
        @PathVariable dummyId: Long,
        @PathVariable matchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportDummyAction("더미 #$dummyId · 1:1 신청 #$matchId 수락") {
            personalMatchController.acceptMatch(qaDummies.principalOf(dummyId), matchId)
        }
        return PERSONAL_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches/{matchId}/reject")
    fun rejectPersonalMatch(
        @PathVariable dummyId: Long,
        @PathVariable matchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportDummyAction("더미 #$dummyId · 1:1 신청 #$matchId 거절") {
            personalMatchController.rejectMatch(qaDummies.principalOf(dummyId), matchId)
        }
        return PERSONAL_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/accept")
    fun acceptGroupMatch(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportDummyAction("더미 #$dummyId · 그룹 #$groupMatchId 수락") {
            groupMatchController.accept(qaDummies.principalOf(dummyId), groupMatchId)
        }
        return GROUP_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/decline")
    fun declineGroupMatch(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportDummyAction("더미 #$dummyId · 그룹 #$groupMatchId 거절") {
            groupMatchController.decline(qaDummies.principalOf(dummyId), groupMatchId)
        }
        return GROUP_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/group-matches/{groupMatchId}/accept-pending-dummies")
    fun acceptGroupMatchForPendingDummies(
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        redirectAttributes.reportEachDummyAction(
            "그룹 #$groupMatchId 대기 중인 더미 수락",
            adminQaService.findPendingDummyIdsIn(groupMatchId),
        ) { dummyId ->
            groupMatchController.accept(qaDummies.principalOf(dummyId), groupMatchId)
        }
        return GROUP_SECTION_REDIRECT
    }

    companion object {
        private const val PERSONAL_SECTION_REDIRECT = "redirect:/admin/qa#personal"
        private const val GROUP_SECTION_REDIRECT = "redirect:/admin/qa#group"
    }
}
