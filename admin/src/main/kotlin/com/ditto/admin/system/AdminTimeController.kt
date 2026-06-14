package com.ditto.admin.system

import com.ditto.admin.auth.AdminPrincipal
import com.ditto.application.system.ServerTimeProvider
import com.ditto.application.system.ServerTimeService
import com.ditto.application.system.SystemStateProvider
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDateTime

/**
 * 서버 시각 조정. 설정 시 DB에 저장하고 그 시각을 사용하며, 해제하면 실제 시각을 사용한다.
 * 설정 변경자(이름·이메일)는 로그인한 어드민으로 기록한다.
 */
@Controller
@RequestMapping("/time-override")
class AdminTimeController(
    private val serverTimeService: ServerTimeService,
    private val serverTimeProvider: ServerTimeProvider,
    private val systemStateProvider: SystemStateProvider,
) {
    @GetMapping
    fun page(model: Model): String {
        model.addAttribute("override", serverTimeService.getOverride())
        model.addAttribute("currentTime", serverTimeProvider.now())
        model.addAttribute("state", systemStateProvider.current())
        model.addAttribute("active", "time")
        return "time-override"
    }

    @PostMapping
    fun override(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") dateTime: LocalDateTime,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        serverTimeService.override(dateTime, admin.name, admin.email)
        redirectAttributes.addFlashAttribute("message", "서버 시각이 설정되었습니다.")
        return "redirect:/time-override"
    }

    @PostMapping("/disable")
    fun disable(redirectAttributes: RedirectAttributes): String {
        serverTimeService.disable()
        redirectAttributes.addFlashAttribute("message", "서버 시각 오버라이드가 해제되었습니다. 실제 시각을 사용합니다.")
        return "redirect:/time-override"
    }
}
