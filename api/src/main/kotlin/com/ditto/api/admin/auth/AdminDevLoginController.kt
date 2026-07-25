package com.ditto.api.admin.auth

import com.ditto.api.admin.config.AdminSecurityConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 로컬 전용 어드민 자동 로그인. 카카오 OAuth·DB 조회 없이 합성 principal 로 세션 인증을 설정한다.
 * local 프로파일에서만 빈으로 등록되므로 prod 에는 핸들러가 없다(404).
 */
@Profile("local")
@Controller
class AdminDevLoginController(
    private val securityContextRepository: SecurityContextRepository,
) {
    @GetMapping("/admin/oauth/dev")
    fun devLogin(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val principal = AdminPrincipal(memberId = LOCAL_DEV_MEMBER_ID, name = "로컬 관리자", email = null)
        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority(AdminSecurityConfig.ROLE_ADMIN)),
        )
        val context = SecurityContextHolder.createEmptyContext().apply { this.authentication = authentication }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)

        return "redirect:/admin"
    }

    companion object {
        /** 실존 회원이 아닌 합성 principal 표시용 ID — 어드민 기능은 memberId 로 회원을 조회하지 않는다. */
        private const val LOCAL_DEV_MEMBER_ID = 0L
    }
}
