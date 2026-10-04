package com.ditto.api.admin.qa

import com.ditto.api.match.controller.GroupMatchController
import com.ditto.api.match.controller.PersonalMatchController
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.system.ServerTimeService
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val serverTimeService: ServerTimeService,
    private val personalMatchController: PersonalMatchController,
    private val groupMatchController: GroupMatchController,
) {
    @GetMapping("/admin/qa")
    fun page(model: Model): String {
        model.addAttribute("console", adminQaService.getConsole())
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
        actAsDummy(redirectAttributes, "더미 #$dummyId → 회원 #$receiverId 1:1 신청") {
            personalMatchController.requestMatch(
                adminQaService.dummyPrincipalOf(dummyId),
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
        actAsDummy(redirectAttributes, "더미 #$dummyId · 1:1 신청 #$matchId 수락") {
            personalMatchController.acceptMatch(adminQaService.dummyPrincipalOf(dummyId), matchId)
        }
        return PERSONAL_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/personal-matches/{matchId}/reject")
    fun rejectPersonalMatch(
        @PathVariable dummyId: Long,
        @PathVariable matchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        actAsDummy(redirectAttributes, "더미 #$dummyId · 1:1 신청 #$matchId 거절") {
            personalMatchController.rejectMatch(adminQaService.dummyPrincipalOf(dummyId), matchId)
        }
        return PERSONAL_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/accept")
    fun acceptGroupMatch(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        actAsDummy(redirectAttributes, "더미 #$dummyId · 그룹 #$groupMatchId 수락") {
            groupMatchController.accept(adminQaService.dummyPrincipalOf(dummyId), groupMatchId)
        }
        return GROUP_SECTION_REDIRECT
    }

    @PostMapping("/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}/decline")
    fun declineGroupMatch(
        @PathVariable dummyId: Long,
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        actAsDummy(redirectAttributes, "더미 #$dummyId · 그룹 #$groupMatchId 거절") {
            groupMatchController.decline(adminQaService.dummyPrincipalOf(dummyId), groupMatchId)
        }
        return GROUP_SECTION_REDIRECT
    }

    /** 한 명씩 앱과 같은 수락을 부른다. 앞사람이 거부돼도 나머지는 계속 수락한다. */
    @PostMapping("/admin/qa/group-matches/{groupMatchId}/accept-pending-dummies")
    fun acceptGroupMatchForPendingDummies(
        @PathVariable groupMatchId: Long,
        redirectAttributes: RedirectAttributes,
    ): String {
        val pendingDummyIds = adminQaService.findPendingDummyIdsIn(groupMatchId)
        if (pendingDummyIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "그룹 #$groupMatchId 에 대기 중인 더미가 없습니다.")
            return GROUP_SECTION_REDIRECT
        }

        val actionLabel = "그룹 #$groupMatchId 대기 중인 더미 ${pendingDummyIds.size}명 수락"
        val rejections = pendingDummyIds.mapNotNull { dummyId ->
            runAppAction { groupMatchController.accept(adminQaService.dummyPrincipalOf(dummyId), groupMatchId) }
                ?.let { "더미 #$dummyId ${it.toDisplayText()}" }
        }
        if (rejections.isEmpty()) {
            reportSuccess(redirectAttributes, actionLabel)
            return GROUP_SECTION_REDIRECT
        }

        val acceptedCount = pendingDummyIds.size - rejections.size
        redirectAttributes.addFlashAttribute(
            "error",
            "$actionLabel: ${acceptedCount}명 수락, ${rejections.size}명 실패. ${rejections.joinToString(", ")}",
        )
        return GROUP_SECTION_REDIRECT
    }

    private fun actAsDummy(redirectAttributes: RedirectAttributes, actionLabel: String, action: () -> Unit) {
        val rejection = runAppAction(action)
        if (rejection == null) {
            reportSuccess(redirectAttributes, actionLabel)
            return
        }
        redirectAttributes.addFlashAttribute("error", "$actionLabel 실패: ${rejection.toDisplayText()}")
    }

    /** 앱이 받는 거부(WarnException)는 돌려주고 그 밖의 예외는 그대로 던진다. QA 중에는 그 거부 자체가 확인할 대상이다. */
    private fun runAppAction(action: () -> Unit): WarnException? =
        runCatching(action).exceptionOrNull()?.let { it as? WarnException ?: throw it }

    private fun reportSuccess(redirectAttributes: RedirectAttributes, actionLabel: String) {
        log.info { "QA 콘솔: $actionLabel" }
        redirectAttributes.addFlashAttribute("message", "$actionLabel 완료")
    }

    private fun WarnException.toDisplayText(): String = "$message (${errorCode.code})"

    companion object {
        private const val PERSONAL_SECTION_REDIRECT = "redirect:/admin/qa#personal"
        private const val GROUP_SECTION_REDIRECT = "redirect:/admin/qa#group"
        private val log = KotlinLogging.logger {}
    }
}
