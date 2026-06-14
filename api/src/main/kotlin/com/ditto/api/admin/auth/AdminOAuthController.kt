package com.ditto.api.admin.auth

import com.ditto.api.admin.config.AdminSecurityConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 어드민 카카오 로그인 진입/콜백. 콜백에서 ADMIN 검증을 통과하면 세션에 인증을 설정한다.
 */
@Controller
class AdminOAuthController(
    private val adminLoginService: AdminLoginService,
    private val securityContextRepository: SecurityContextRepository,
) {
    @GetMapping("/admin/login")
    fun loginPage(
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) logout: String?,
        model: Model,
    ): String {
        if (error != null) model.addAttribute("error", "로그인할 수 없습니다. 관리자 권한이 있는 계정인지 확인해 주세요.")
        if (logout != null) model.addAttribute("message", "로그아웃되었습니다.")
        return "login"
    }

    @GetMapping("/admin/oauth/kakao")
    fun kakaoLogin(): String = "redirect:" + adminLoginService.authorizationUrl()

    @GetMapping("/admin/oauth/kakao/callback")
    fun kakaoCallback(
        @RequestParam code: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val principal = try {
            adminLoginService.login(code)
        } catch (e: AdminLoginDeniedException) {
            log.info { "어드민 로그인 거부: ${e.message}" }
            return "redirect:/admin/login?error"
        }

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
        private val log = KotlinLogging.logger {}
    }
}
