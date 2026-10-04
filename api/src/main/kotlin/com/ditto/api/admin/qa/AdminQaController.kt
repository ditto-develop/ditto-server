package com.ditto.api.admin.qa

import com.ditto.api.match.controller.PersonalMatchController
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.system.ServerTimeProvider
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
    private val serverTimeProvider: ServerTimeProvider,
    private val personalMatchController: PersonalMatchController,
) {
    @GetMapping("/admin/qa")
    fun page(model: Model): String {
        model.addAttribute("console", adminQaService.getConsole())
        model.addAttribute("currentTime", serverTimeProvider.now())
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

    /** 앱이 받는 것과 같은 거부(WarnException)는 화면에 코드와 함께 보여준다. QA 중에는 그 거부 자체가 확인할 대상이다. */
    private fun actAsDummy(redirectAttributes: RedirectAttributes, actionLabel: String, action: () -> Unit) {
        runCatching(action)
            .onSuccess {
                log.info { "QA 콘솔: $actionLabel" }
                redirectAttributes.addFlashAttribute("message", "$actionLabel 완료")
            }
            .onFailure { exception ->
                if (exception !is WarnException) throw exception
                redirectAttributes.addFlashAttribute(
                    "error",
                    "$actionLabel 실패: ${exception.message} (${exception.errorCode.code})",
                )
            }
    }

    companion object {
        private const val PERSONAL_SECTION_REDIRECT = "redirect:/admin/qa#personal"
        private val log = KotlinLogging.logger {}
    }
}
