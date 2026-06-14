package com.ditto.admin

import com.ditto.application.system.SystemStateProvider
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(
    private val systemStateProvider: SystemStateProvider,
) {
    @GetMapping("/")
    fun dashboard(model: Model): String {
        model.addAttribute("state", systemStateProvider.current())
        model.addAttribute("active", "dashboard")
        return "dashboard"
    }
}
