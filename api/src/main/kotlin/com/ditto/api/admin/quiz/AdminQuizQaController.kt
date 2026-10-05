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
) {
    @PostMapping("/admin/quiz-sets/{id}/qa/reset-matching")
    fun resetMatching(
        @PathVariable id: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizQaService.resetMatching(id) }
        .fold(
            onSuccess = { summary ->
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 매칭 기록 초기화: ${summary.toDisplayText()}" }
                redirectAttributes.addFlashAttribute("message", "매칭 기록을 초기화했습니다. ${summary.toDisplayText()}")
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
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 강제 삭제: ${summary.toDisplayText()}" }
                redirectAttributes.addFlashAttribute("message", "퀴즈셋을 강제 삭제했습니다. ${summary.toDisplayText()}")
                "redirect:/admin/quiz-sets"
            },
            onFailure = { exception -> redirectAfterFailure(id, exception, redirectAttributes) },
        )

    private fun redirectAfterFailure(id: Long, exception: Throwable, redirectAttributes: RedirectAttributes): String {
        if (exception !is WarnException) throw exception

        log.warn { "퀴즈셋 #$id QA 도구 요청 거부: ${exception.message}" }
        redirectAttributes.addFlashAttribute("error", exception.message)
        if (exception.errorCode == ErrorCode.NOT_FOUND) return "redirect:/admin/quiz-sets"
        return "redirect:/admin/quiz-sets/$id"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
