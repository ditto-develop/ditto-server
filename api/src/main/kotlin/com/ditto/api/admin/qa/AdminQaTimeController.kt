package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.ServerTimeService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDateTime

/** QA 콘솔에서 서버 시각을 옮기고 보던 화면으로 돌아온다. 저장은 시간 조정 화면과 같은 [ServerTimeService]다. */
@Controller
class AdminQaTimeController(
    private val serverTimeService: ServerTimeService,
) {
    @PostMapping("/admin/qa/server-time")
    fun overrideServerTime(
        @RequestParam dateTime: LocalDateTime,
        @RequestParam(defaultValue = QaRoutes.CONSOLE_PATH) returnTo: String,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        serverTimeService.override(dateTime, admin.name, admin.email)
        redirectAttributes.addFlashAttribute(
            "message",
            "서버 시각을 ${QaTimeFormat.format(dateTime)}로 옮겼습니다. 방이 열리고 닫히는 건 1분 안에 반영됩니다.",
        )
        return QaRoutes.backTo(returnTo)
    }

    @PostMapping("/admin/qa/server-time/disable")
    fun disableServerTimeOverride(
        @RequestParam(defaultValue = QaRoutes.CONSOLE_PATH) returnTo: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        serverTimeService.disable()
        redirectAttributes.addFlashAttribute("message", "시간 조정을 껐습니다. 실제 시각을 씁니다.")
        return QaRoutes.backTo(returnTo)
    }
}
