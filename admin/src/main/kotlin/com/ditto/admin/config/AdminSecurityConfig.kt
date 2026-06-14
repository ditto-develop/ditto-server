package com.ditto.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * 어드민 보안 설정(세션 기반). 로그인/카카오 콜백/정적 리소스/헬스만 공개하고, 나머지는 ROLE_ADMIN 만 접근한다.
 * CSRF 는 활성 상태로 둔다(Thymeleaf 폼이 토큰을 자동 포함).
 */
@Configuration
class AdminSecurityConfig {

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun adminFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
    ): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/login", permitAll)
                authorize("/oauth/**", permitAll)
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize("/favicon.ico", permitAll)
                authorize("/actuator/**", permitAll)
                authorize(anyRequest, hasRole("ADMIN"))
            }
            securityContext {
                this.securityContextRepository = securityContextRepository
            }
            formLogin { disable() }
            httpBasic { disable() }
            logout {
                logoutUrl = "/logout"
                logoutSuccessUrl = "/login?logout"
                invalidateHttpSession = true
            }
            exceptionHandling {
                authenticationEntryPoint = LoginUrlAuthenticationEntryPoint("/login")
            }
        }
        return http.build()
    }

    companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
    }
}
