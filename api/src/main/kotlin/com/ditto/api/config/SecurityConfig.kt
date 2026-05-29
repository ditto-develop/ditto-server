package com.ditto.api.config

import com.ditto.api.config.auth.ApiKeyAuthFilter
import com.ditto.api.config.auth.ApiKeyProperties
import com.ditto.api.config.auth.CorsProperties
import com.ditto.api.config.auth.JwtAuthenticationFilter
import com.ditto.api.config.auth.JwtTokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val apiKeyProperties: ApiKeyProperties,
    private val jwtTokenProvider: JwtTokenProvider,
    private val corsProperties: CorsProperties,
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
     * API Key만 필요 (소셜 로그인, 토큰 갱신 등 JWT 불필요)
     */
    @Bean
    @Order(3)
    fun apiKeyOnlySecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(
                "/api/v1/users/social-login/**",
                "/api/v1/users/auth/refresh",
                "/api/v1/users",
                "/api/v1/users/nickname/**",
            )
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
    }

    /**
     * API — API Key + JWT 인증 필수
     */
    @Bean
    @Order(4)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(JwtAuthenticationFilter(jwtTokenProvider), ApiKeyAuthFilter::class.java)
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
    }

    /**
     * 그 외 모든 경로 — 차단
     */
    @Bean
    @Order(5)
    fun defaultSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().denyAll() }
            .build()
    }

    /**
     * CORS 설정 — 허용 origin은 `ditto.cors.allowed-origins`(yml/환경변수)로 관리
     * 인증 헤더(Authorization, X-API-Key)를 allowedHeaders에 포함한다.
     * Bearer 토큰 기반이라 쿠키를 쓰지 않으므로 allowCredentials는 false.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-API-Key")
            allowCredentials = false
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }
}
