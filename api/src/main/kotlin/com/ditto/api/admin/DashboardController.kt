package com.ditto.api.admin

import com.ditto.api.admin.feedback.AdminFeedbackService
import com.ditto.api.admin.launchnotification.AdminLaunchNotificationService
import com.ditto.api.admin.quiz.AdminQuizService
import com.ditto.api.admin.report.AdminReportService
import com.ditto.api.system.SystemStateProvider
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.memberreport.entity.MemberReportStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(
    private val systemStateProvider: SystemStateProvider,
    private val adminReportService: AdminReportService,
    private val adminFeedbackService: AdminFeedbackService,
    private val adminLaunchNotificationService: AdminLaunchNotificationService,
    private val adminQuizService: AdminQuizService,
) {
    @GetMapping("/admin")
    fun dashboard(model: Model): String {
        val state = systemStateProvider.current()
        val receivedReports = adminReportService.listReports(MemberReportStatus.RECEIVED, state.dateTime)
        model.addAttribute("state", state)
        model.addAttribute("receivedReportCount", receivedReports.size)
        model.addAttribute("overdueReportCount", receivedReports.count { it.overdue })
        model.addAttribute("receivedFeedbackCount", adminFeedbackService.listFeedbacks(FeedbackStatus.RECEIVED, null).size)
        model.addAttribute(
            "pendingLaunchNotificationCount",
            LaunchPlatform.entries.sumOf { adminLaunchNotificationService.summary(it).pendingCount },
        )
        model.addAttribute("weekQuizSets", adminQuizService.listQuizSetsInWeek(state.weekStartedOn))
        model.addAttribute("active", "dashboard")
        return "dashboard"
    }
}
