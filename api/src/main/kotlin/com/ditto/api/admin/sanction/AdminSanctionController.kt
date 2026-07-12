package com.ditto.api.admin.sanction

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.WarnException
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
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
 * 회원별 제재 관리 — 이력 조회, 직권 제재(허위 신고자 조치 겸용), 직권 해제(오처리 정정).
 */
@Controller
class AdminSanctionController(
    private val adminSanctionService: AdminSanctionService,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/admin/members/{id}/sanctions")
    fun page(@PathVariable id: Long, model: Model): String {
        model.addAttribute("view", adminSanctionService.memberSanctions(id))
        model.addAttribute("levels", SanctionLevel.entries)
        model.addAttribute("origins", MANUAL_ORIGINS)
        model.addAttribute("active", "member")
        return "sanction/member"
    }

    @PostMapping("/admin/members/{id}/sanctions")
    fun impose(
        @PathVariable id: Long,
        @RequestParam level: SanctionLevel,
        @RequestParam origin: SanctionOrigin,
        @RequestParam(required = false) note: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching {
            adminSanctionService.impose(
                memberId = id,
                level = level,
                origin = origin,
                admin = admin,
                now = serverTimeProvider.now(),
                note = note?.takeIf { it.isNotBlank() },
            )
        }
            .onSuccess {
                log.info { "어드민[${admin.displayName}] 이 회원 #$id 에 직권 제재 ${level.name}(${origin.name}) 적용" }
                redirectAttributes.addFlashAttribute("message", "'${level.description}' 제재를 적용했습니다.")
            }
            .onFailure { e ->
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
            }
        return "redirect:/admin/members/$id/sanctions"
    }

    @PostMapping("/admin/sanctions/{id}/lift")
    fun lift(
        @PathVariable id: Long,
        @RequestParam memberId: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching { adminSanctionService.lift(id, serverTimeProvider.now()) }
            .onSuccess {
                log.info { "어드민[${admin.displayName}] 이 제재 #$id 를 직권 해제" }
                redirectAttributes.addFlashAttribute("message", "제재 #$id 를 해제했습니다.")
            }
            .onFailure { e ->
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
            }
        return "redirect:/admin/members/$memberId/sanctions"
    }

    companion object {
        private val log = KotlinLogging.logger {}

        // 직권 제재의 경위 — REPORTED는 신고 검토 경로 전용이라 폼에서 제외한다.
        private val MANUAL_ORIGINS = listOf(SanctionOrigin.MANUAL, SanctionOrigin.FALSE_REPORT)
    }
}
