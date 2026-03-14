package com.ditto.api.config

import com.ditto.api.config.auth.ApiKeyAuthFilter
import com.ditto.api.config.auth.ApiKeyProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val apiKeyProperties: ApiKeyProperties,
) {

    /**
     * Actuator 엔드포인트 — localhost만 허용 (Alloy sidecar용)
     * 외부 접근은 Security Group + CloudFront Behavior에서 차단
     */
    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/actuator/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    /**
     * 인증 없이 허용 (CloudFront 헬스체크, Swagger UI)
     */
    @Bean
    @Order(2)
    fun healthSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/health", "/docs/**", "/swagger-ui/**", "/v3/api-docs/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    /**
     * API — API Key 인증 필수
     */
    @Bean
    @Order(3)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
    }

    /**
     * 그 외 모든 경로 — 차단
     */
    @Bean
    @Order(4)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().denyAll() }
            .build()
    }
}
