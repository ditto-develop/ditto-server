package com.ditto.api.admin.member

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.common.exception.WarnException
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
 * 회원 검색(닉네임·회원 ID, 이메일), 회원별 퀴즈 현황, 권한 변경.
 * 같은 이메일에 여러 회원이 있을 수 있어 목록으로 보여주고 개별 변경한다.
 */
@Controller
class AdminMemberController(
    private val adminMemberService: AdminMemberService,
    private val adminMemberQuizService: AdminMemberQuizService,
) {
    @GetMapping("/admin/members")
    fun page(
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        // 검색창은 하나다. 예전 링크의 email 파라미터도 같은 검색으로 받는다. 닉네임에는 @ 가 들어갈 수 없다.
        val query = q?.takeIf { it.isNotBlank() } ?: email.orEmpty()
        model.addAttribute("q", query)
        when {
            query.isBlank() -> Unit
            query.contains(EMAIL_AT_SIGN) -> model.addAttribute("members", adminMemberService.searchByEmail(query))
            else -> model.addAttribute("searchResult", adminMemberService.searchByNicknameOrId(query))
        }
        model.addAttribute("roles", MemberRole.entries)
        model.addAttribute("admins", adminMemberService.listAdmins())
        model.addAttribute("active", "member")
        return "member/list"
    }

    @GetMapping("/admin/members/{id}/quizzes")
    fun quizzes(
        @PathVariable id: Long,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) email: String?,
        model: Model,
    ): String {
        val backQuery = q?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: "#$id"
        model.addAttribute("view", adminMemberQuizService.getMemberQuizzes(id))
        model.addAttribute("backQuery", backQuery)
        model.addAttribute("active", "member")
        return "member/quizzes"
    }

    @PostMapping("/admin/members/{id}/role")
    fun changeRole(
        @PathVariable id: Long,
        @RequestParam role: MemberRole,
        @RequestParam(required = false) q: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (!q.isNullOrBlank()) redirectAttributes.addAttribute("q", q)
        runCatching { adminMemberService.changeRole(id, role, admin.memberId) }
            .onSuccess {
                log.info { "어드민[${admin.displayName}] 이 회원 #$id 의 권한을 $role 로 변경" }
                redirectAttributes.addFlashAttribute("message", "회원 #${id}의 권한을 바꿨습니다: ${role.description}")
            }
            .onFailure { exception ->
                if (exception !is WarnException) throw exception
                redirectAttributes.addFlashAttribute("error", exception.message)
            }
        return "redirect:/admin/members"
    }

    companion object {
        private val log = KotlinLogging.logger {}
        private const val EMAIL_AT_SIGN = '@'
    }
}
