package com.ditto.api.admin.system

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.system.ServerTimeService
import com.ditto.api.system.SystemStateProvider
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDateTime

/**
 * 서버 시각 조정. 설정 시 DB에 저장하고 그 시각을 사용하며, 해제하면 실제 시각을 사용한다.
 * 설정 변경자(이름·이메일)는 로그인한 어드민으로 기록한다.
 */
@Controller
class AdminTimeController(
    private val serverTimeService: ServerTimeService,
    private val serverTimeProvider: ServerTimeProvider,
    private val systemStateProvider: SystemStateProvider,
) {
    @GetMapping("/admin/time-override")
    fun page(model: Model): String {
        model.addAttribute("override", serverTimeService.getOverride())
        model.addAttribute("currentTime", serverTimeProvider.now())
        model.addAttribute("state", systemStateProvider.current())
        model.addAttribute("active", "time")
        return "time-override"
    }

    // datetime-local 입력은 ISO_LOCAL_DATE_TIME("yyyy-MM-ddTHH:mm") 이라 스프링 기본 변환으로 바인딩된다.
    @PostMapping("/admin/time-override")
    fun override(
        @RequestParam dateTime: LocalDateTime,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        serverTimeService.override(dateTime, admin.name, admin.email)
        redirectAttributes.addFlashAttribute("message", "서버 시각이 설정되었습니다.")
        return "redirect:/admin/time-override"
    }

    @PostMapping("/admin/time-override/disable")
    fun disable(redirectAttributes: RedirectAttributes): String {
        serverTimeService.disable()
        redirectAttributes.addFlashAttribute("message", "서버 시각 오버라이드가 해제되었습니다. 실제 시각을 사용합니다.")
        return "redirect:/admin/time-override"
    }
}
