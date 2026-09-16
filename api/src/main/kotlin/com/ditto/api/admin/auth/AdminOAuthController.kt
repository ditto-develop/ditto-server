package com.ditto.api.admin.auth

import com.ditto.api.admin.config.AdminSecurityConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 어드민 소셜 로그인 진입/콜백. 콜백에서 ADMIN 검증을 통과하면 세션에 인증을 설정한다.
 *
 * 카카오는 `GET ?code=...`, 애플은 **폼 POST**로 콜백이 온다 — 애플은 scope 를 요청하려면
 * `response_mode=form_post` 가 필수이기 때문이다(ADR 0023). 그래서 애플 콜백 경로만
 * [com.ditto.api.admin.config.AdminSecurityConfig] 에서 CSRF 예외로 둔다.
 */
@Controller
class AdminOAuthController(
    private val adminLoginService: AdminLoginService,
    private val securityContextRepository: SecurityContextRepository,
    private val environment: Environment,
) {
    @GetMapping("/admin/login")
    fun loginPage(
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) logout: String?,
        model: Model,
    ): String {
        if (error != null) model.addAttribute("error", "로그인할 수 없습니다. 관리자 권한이 있는 계정인지 확인해 주세요.")
        if (logout != null) model.addAttribute("message", "로그아웃되었습니다.")
        model.addAttribute("devLoginEnabled", environment.matchesProfiles("local"))
        return "login"
    }

    @GetMapping("/admin/oauth/kakao")
    fun kakaoLogin(): String = "redirect:" + adminLoginService.authorizationUrl()

    @GetMapping("/admin/oauth/apple")
    fun appleLogin(): String = "redirect:" + adminLoginService.appleAuthorizationUrl()

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

        authenticate(principal, request, response)

        return "redirect:/admin"
    }

    /**
     * 애플 웹 로그인 콜백. 폼에는 `code`·`id_token`·`state`·`user` 가 실리지만 **`id_token` 만 쓴다** —
     * 검증이 곧 인증이라 인가 코드 교환(=클라이언트 시크릿)이 필요 없다.
     *
     * 이름·이메일을 담은 `user` 필드는 읽지 않는다. 어드민은 회원을 생성하지 않고 기존 회원을 찾기만 하므로
     * 최초 인가에서만 오는 그 값이 필요 없다.
     */
    @PostMapping(
        "/admin/oauth/apple/callback",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
    )
    fun appleCallback(
        @RequestParam("id_token") idToken: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val principal = try {
            adminLoginService.loginWithAppleIdToken(idToken)
        } catch (e: AdminLoginDeniedException) {
            log.info { "어드민 애플 로그인 거부: ${e.message}" }
            return "redirect:/admin/login?error"
        }

        authenticate(principal, request, response)

        return "redirect:/admin"
    }

    /** 세션에 어드민 인증을 심는다 — 제공자와 무관하게 부여하는 권한은 ROLE_ADMIN 하나다. */
    private fun authenticate(
        principal: AdminPrincipal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            listOf(SimpleGrantedAuthority(AdminSecurityConfig.ROLE_ADMIN)),
        )
        val context = SecurityContextHolder.createEmptyContext().apply { this.authentication = authentication }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
