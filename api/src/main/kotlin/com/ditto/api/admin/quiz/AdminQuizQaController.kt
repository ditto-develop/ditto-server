package com.ditto.api.admin.quiz

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.util.UriComponentsBuilder

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
                detailRedirect(id)
            },
            onFailure = { exception -> redirectAfterFailure(exception, redirectAttributes, detailRedirect(id)) },
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
                QUIZ_SET_LIST_REDIRECT
            },
            onFailure = { exception -> redirectAfterFailure(exception, redirectAttributes, detailRedirect(id)) },
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
                redirectAttributes.addFlashAttribute("message", "답·진행을 초기화했습니다. $erased. $NEXT_STEPS_AFTER_ANSWER_RESET")
                detailRedirect(id)
            },
            onFailure = { exception -> redirectAfterFailure(exception, redirectAttributes, detailRedirect(id)) },
        )

    @PostMapping("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers")
    fun resetMemberAnswers(
        @PathVariable id: Long,
        @PathVariable memberId: Long,
        @RequestParam("q", required = false) searchQuery: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String = runCatching { adminQuizAnswerResetService.resetMemberAnswers(id, memberId) }
        .fold(
            onSuccess = { summary ->
                log.info { "어드민[${admin.displayName}] 이 퀴즈셋 #$id 회원 #$memberId 답·진행 초기화" }
                redirectAttributes.addFlashAttribute("message", "${summary.toDisplayText()}의 답·진행을 초기화했습니다.")
                // 초기화한 회원은 진행 행이 지워져 목록에서 빠지므로, 그 회원만 찾던 검색어로 돌아가면 빈 표만 남는다.
                val searchQueryAfterReset = searchQuery.takeUnless { it?.trim() == "#$memberId" }
                participantsRedirect(id, searchQueryAfterReset)
            },
            onFailure = { exception ->
                redirectAfterFailure(exception, redirectAttributes, participantsRedirect(id, searchQuery))
            },
        )

    private fun redirectAfterFailure(
        exception: Throwable,
        redirectAttributes: RedirectAttributes,
        redirectOnWarn: String,
    ): String {
        if (exception !is WarnException) throw exception

        log.warn { "QA 도구 요청 거부($redirectOnWarn): ${exception.message}" }
        redirectAttributes.addFlashAttribute("error", exception.message)
        // NOT_FOUND 는 퀴즈셋이 없다는 뜻이라 돌아갈 화면이 없다.
        if (exception.errorCode == ErrorCode.NOT_FOUND) return QUIZ_SET_LIST_REDIRECT
        return redirectOnWarn
    }

    private fun detailRedirect(id: Long): String = "redirect:/admin/quiz-sets/$id"

    private fun participantsRedirect(quizSetId: Long, searchQuery: String?): String {
        val trimmedQuery = searchQuery?.trim().orEmpty()
        val participantsUri = UriComponentsBuilder.fromPath("/admin/quiz-sets/{id}/participants")
        if (trimmedQuery.isNotEmpty()) participantsUri.queryParam("q", "{q}")
        val encodedUri = participantsUri.encode().buildAndExpand(mapOf("id" to quizSetId, "q" to trimmedQuery))
        return "redirect:" + encodedUri.toUriString()
    }

    companion object {
        private const val QUIZ_SET_LIST_REDIRECT = "redirect:/admin/quiz-sets"
        private const val NEXT_STEPS_AFTER_ANSWER_RESET =
            "다시 하려면 퀴즈 기간(월~수)에 다시 풀고, 서버 시각을 목요일로 맞춘 뒤 매칭 화면에서 매칭하세요."
        private val log = KotlinLogging.logger {}
    }
}
