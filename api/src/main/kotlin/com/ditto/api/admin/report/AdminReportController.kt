package com.ditto.api.admin.report

import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.memberreport.entity.MemberReportStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 신고 검토 화면 — 접수 대기열(24시간 SLA)과 검토에 필요한 컨텍스트(이력·추천 차수·가이드라인)를 제공한다.
 */
@Controller
class AdminReportController(
    private val adminReportService: AdminReportService,
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
        model.addAttribute("active", "report")
        return "report/detail"
    }
}
