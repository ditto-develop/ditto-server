package com.ditto.api.admin.quiz

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class AdminQuizQaController(
    private val adminQuizQaService: AdminQuizQaService,
    private val adminQuizAnswerResetService: AdminQuizAnswerResetService,
) {
    @PostMapping("/admin/quiz-sets/{id}/qa/reset-matching")
    fun resetMatching(
        @PathVariable id: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizQaService.resetMatching(id) }
        .fold(
            onSuccess = { summary ->
                val erased = summary.toDisplayText()
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 매칭 기록 초기화: $erased" }
                redirectAttributes.addFlashAttribute("message", "매칭 기록을 초기화했습니다. $erased")
                "redirect:/admin/quiz-sets/$id"
            },
            onFailure = { exception -> redirectAfterFailure(id, exception, redirectAttributes) },
        )

    @PostMapping("/admin/quiz-sets/{id}/qa/force-delete")
    fun forceDelete(
        @PathVariable id: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizQaService.forceDelete(id) }
        .fold(
            onSuccess = { summary ->
                val erased = summary.toDisplayText()
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 강제 삭제: $erased" }
                redirectAttributes.addFlashAttribute(
                    "message",
                    "퀴즈셋 #$id(${summary.quizSetTitle})을 강제 삭제했습니다. $erased",
                )
                "redirect:/admin/quiz-sets"
            },
            onFailure = { exception -> redirectAfterFailure(id, exception, redirectAttributes) },
        )

    @PostMapping("/admin/quiz-sets/{id}/qa/reset-answers")
    fun resetAllAnswers(
        @PathVariable id: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizAnswerResetService.resetAllAnswers(id) }
        .fold(
            onSuccess = { summary ->
                val erased = summary.toDisplayText()
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 답·진행 초기화: $erased" }
                redirectAttributes.addFlashAttribute("message", "답·진행을 초기화했습니다. $erased")
                "redirect:/admin/quiz-sets/$id"
            },
            onFailure = { exception -> redirectAfterFailure(id, exception, redirectAttributes) },
        )

    @PostMapping("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers")
    fun resetMemberAnswers(
        @PathVariable id: Long,
        @PathVariable memberId: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        val participantsPage = "redirect:/admin/quiz-sets/$id/participants"
        return runCatching { adminQuizAnswerResetService.resetMemberAnswers(id, memberId) }
            .fold(
                onSuccess = {
                    log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 회원 #$memberId 답·진행 초기화" }
                    redirectAttributes.addFlashAttribute("message", "회원 #$memberId 의 답·진행을 초기화했습니다.")
                    participantsPage
                },
                onFailure = { exception -> redirectAfterFailure(id, exception, redirectAttributes, participantsPage) },
            )
    }

    // NOT_FOUND 는 퀴즈셋이 없다는 뜻이라 돌아갈 화면이 없어 목록으로 보낸다.
    private fun redirectAfterFailure(
        id: Long,
        exception: Throwable,
        redirectAttributes: RedirectAttributes,
        returnPage: String = "redirect:/admin/quiz-sets/$id",
    ): String {
        if (exception !is WarnException) throw exception

        log.warn { "퀴즈셋 #$id QA 도구 요청 거부: ${exception.message}" }
        redirectAttributes.addFlashAttribute("error", exception.message)
        if (exception.errorCode == ErrorCode.NOT_FOUND) return "redirect:/admin/quiz-sets"
        return returnPage
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
