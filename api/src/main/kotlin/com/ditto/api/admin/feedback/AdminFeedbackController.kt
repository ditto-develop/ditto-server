package com.ditto.api.admin.feedback

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class AdminFeedbackController(
    private val adminFeedbackService: AdminFeedbackService,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/admin/feedbacks")
    fun list(
        @RequestParam(required = false) status: FeedbackStatus?,
        @RequestParam(required = false) type: FeedbackType?,
        model: Model,
    ): String {
        val selectedStatus = status ?: FeedbackStatus.RECEIVED
        model.addAttribute("feedbacks", adminFeedbackService.listFeedbacks(selectedStatus, type))
        model.addAttribute("statuses", FeedbackStatus.entries)
        model.addAttribute("types", FeedbackType.entries)
        model.addAttribute("selectedStatus", selectedStatus)
        model.addAttribute("selectedType", type)
        model.addAttribute("active", "feedback")
        return "feedback/list"
    }

    @GetMapping("/admin/feedbacks/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        model.addAttribute("feedback", adminFeedbackService.getFeedbackDetail(id))
        model.addAttribute("active", "feedback")
        return "feedback/detail"
    }

    @PostMapping("/admin/feedbacks/{id}/complete")
    fun complete(
        @PathVariable id: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching { adminFeedbackService.completeFeedback(id, admin, serverTimeProvider.now()) }
            .onSuccess {
                log.info { "어드민[${admin.displayName}] 이 의견 #$id 를 처리 완료" }
                redirectAttributes.addFlashAttribute("message", "의견 #$id 를 처리 완료했습니다.")
            }
            .onFailure { e ->
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
            }
        return "redirect:/admin/feedbacks/$id"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
