package com.ditto.api.admin.report

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.report.dto.ReviewDecision
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.entity.MemberReportStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 신고 검토 화면 — 접수 대기열(24시간 SLA)과 검토에 필요한 컨텍스트(이력·추천 차수·가이드라인)를 제공한다.
 */
@Controller
class AdminReportController(
    private val adminReportService: AdminReportService,
    private val adminReportReviewService: AdminReportReviewService,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/admin/reports")
    fun list(
        @RequestParam(required = false) status: MemberReportStatus?,
        model: Model,
    ): String {
        val selected = status ?: MemberReportStatus.RECEIVED
        model.addAttribute("reports", adminReportService.listReports(selected, serverTimeProvider.now()))
        model.addAttribute("statuses", MemberReportStatus.entries)
        model.addAttribute("selected", selected)
        model.addAttribute("active", "report")
        return "report/list"
    }

    @GetMapping("/admin/reports/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        model.addAttribute("report", adminReportService.getReportDetail(id, serverTimeProvider.now()))
        model.addAttribute("decisions", ReviewDecision.entries)
        model.addAttribute("active", "report")
        return "report/detail"
    }

    @PostMapping("/admin/reports/{id}/action")
    fun review(
        @PathVariable id: Long,
        @RequestParam decision: ReviewDecision,
        @RequestParam(required = false) reviewNote: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching {
            adminReportReviewService.review(
                reportId = id,
                decision = decision,
                note = reviewNote?.takeIf { it.isNotBlank() },
                admin = admin,
                now = serverTimeProvider.now(),
            )
        }
            .onSuccess {
                log.info { "어드민[${admin.displayName}] 이 신고 #$id 를 ${decision.name} 로 처리" }
                redirectAttributes.addFlashAttribute("message", "신고 #$id 를 '${decision.description}' 로 처리했습니다.")
            }
            .onFailure { e ->
                // 입력값·상태 오류(WarnException)는 화면에 안내하고, 예기치 못한 예외는 전역 핸들러로 전파한다.
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
            }
        return "redirect:/admin/reports/$id"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
