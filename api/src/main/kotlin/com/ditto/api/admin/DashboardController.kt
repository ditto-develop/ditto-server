package com.ditto.api.admin

import com.ditto.api.admin.feedback.AdminFeedbackService
import com.ditto.api.admin.launchnotification.AdminLaunchNotificationService
import com.ditto.api.admin.quiz.AdminQuizService
import com.ditto.api.admin.report.AdminReportService
import com.ditto.api.system.SystemStateProvider
import com.ditto.domain.feedback.entity.FeedbackStatus
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
        val weekQuizSets = adminQuizService.listQuizSetsInWeek(state.weekStartedOn)
        model.addAttribute("state", state)
        model.addAttribute("receivedReportCount", adminReportService.countReceivedReports())
        model.addAttribute("overdueReportCount", adminReportService.countOverdueReports(state.dateTime))
        model.addAttribute("receivedFeedbackCount", adminFeedbackService.countFeedbacks(FeedbackStatus.RECEIVED))
        model.addAttribute("pendingLaunchNotificationCount", adminLaunchNotificationService.countPendingRequests())
        model.addAttribute("weekQuizSets", weekQuizSets)
        model.addAttribute("hasActiveQuizSetThisWeek", weekQuizSets.any { it.isActive })
        model.addAttribute("active", "dashboard")
        return "dashboard"
    }
}
