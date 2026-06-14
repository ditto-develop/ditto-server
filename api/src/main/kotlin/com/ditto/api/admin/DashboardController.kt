package com.ditto.api.admin

import com.ditto.api.system.SystemStateProvider
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardController(
    private val systemStateProvider: SystemStateProvider,
) {
    @GetMapping("/admin")
    fun dashboard(model: Model): String {
        model.addAttribute("state", systemStateProvider.current())
        model.addAttribute("active", "dashboard")
        return "dashboard"
    }
}
