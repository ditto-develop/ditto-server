package com.ditto.api.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * 어드민 경로(admin 하위) 전용 보안 체인 — api 의 무상태 JWT 체인과 분리된 세션 기반.
 *
 * api 의 [com.ditto.api.config.SecurityConfig] 체인들보다 먼저 평가되도록 [Order] 0 으로 두고,
 * securityMatcher 로 admin 하위 경로만 담당한다. 로그인·카카오 콜백·정적 리소스만 공개하고 나머지는 ROLE_ADMIN.
 * CSRF 는 활성(Thymeleaf 폼이 토큰 자동 포함).
 */
@Configuration
class AdminSecurityConfig {

    @Bean
    fun adminSecurityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    @Order(0)
    fun adminFilterChain(
        http: HttpSecurity,
        adminSecurityContextRepository: SecurityContextRepository,
    ): SecurityFilterChain {
        http {
            securityMatcher("/admin/**")
            authorizeHttpRequests {
                authorize("/admin/login", permitAll)
                authorize("/admin/oauth/**", permitAll)
                authorize("/admin/css/**", permitAll)
                authorize("/admin/js/**", permitAll)
                authorize("/admin/fonts/**", permitAll)
                authorize(anyRequest, hasRole("ADMIN"))
            }
            securityContext {
                this.securityContextRepository = adminSecurityContextRepository
            }
            formLogin { disable() }
            httpBasic { disable() }
            logout {
                logoutUrl = "/admin/logout"
                logoutSuccessUrl = "/admin/login?logout"
                invalidateHttpSession = true
            }
            exceptionHandling {
                authenticationEntryPoint = LoginUrlAuthenticationEntryPoint("/admin/login")
            }
        }
        return http.build()
    }

    companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
