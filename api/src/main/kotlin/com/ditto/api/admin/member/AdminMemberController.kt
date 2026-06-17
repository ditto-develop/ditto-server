package com.ditto.api.admin.member

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.domain.member.entity.MemberRole
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
 * 회원 검색(이메일) 및 권한 변경. 같은 이메일에 여러 회원이 있을 수 있어 목록으로 보여주고 개별 변경한다.
 */
@Controller
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
) {
    @GetMapping("/admin/members")
    fun page(@RequestParam(required = false) email: String?, model: Model): String {
        model.addAttribute("email", email ?: "")
        model.addAttribute("roles", MemberRole.entries)
        model.addAttribute("admins", adminMemberService.listAdmins())
        model.addAttribute("searched", !email.isNullOrBlank())
        if (!email.isNullOrBlank()) {
            model.addAttribute("members", adminMemberService.searchByEmail(email))
        }
        model.addAttribute("active", "member")
        return "member/list"
    }

    @PostMapping("/admin/members/{id}/role")
    fun changeRole(
        @PathVariable id: Long,
        @RequestParam role: MemberRole,
        @RequestParam(required = false) email: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        adminMemberService.changeRole(id, role)
        log.info { "어드민[${admin.displayName}] 이 회원 #$id 의 권한을 $role 로 변경" }
        redirectAttributes.addFlashAttribute("message", "회원 #$id 의 권한을 ${role.label} 로 변경했습니다.")
        if (!email.isNullOrBlank()) redirectAttributes.addAttribute("email", email)
        return "redirect:/admin/members"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
