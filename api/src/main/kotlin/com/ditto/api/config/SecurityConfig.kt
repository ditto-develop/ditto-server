package com.ditto.api.config

import com.ditto.api.config.auth.ApiKeyAuthFilter
import com.ditto.api.config.auth.ApiKeyProperties
import com.ditto.api.config.auth.CorsProperties
import com.ditto.api.config.auth.JwtAuthenticationFilter
import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.member.repository.MemberRepository
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
    private val memberRepository: MemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
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
     * 인증 불필요 (permitAll) — X-API-Key·JWT 없이 접근 가능한 공개 엔드포인트
     */
    @Bean
    @Order(3)
    fun publicApiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(
                "/api/v1/users/social-login/*",
                "/api/v1/users/social-login/*/callback",
            )
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    /**
     * API Key만 필요 (토큰 갱신·닉네임 중복확인 등 JWT 불필요)
     *
     * 네이티브 소셜 로그인(social-login 하위 native 경로)도 여기 속한다 — 로그인 전 호출이라 JWT가 없고,
     * 소셜 액세스 토큰을 우리 토큰으로 교환하는 것 외에는 아무것도 하지 않는다.
     * 리다이렉트 로그인(@Order(3), permitAll)과 달리 브라우저 리다이렉트가 아니라 앱이 직접 호출하므로
     * API Key를 실을 수 있어 한 단계 조인다.
     */
    @Bean
    @Order(4)
    fun apiKeyOnlySecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher(
                "/api/v1/users/auth/refresh",
                "/api/v1/users/nickname/**",
                "/api/v1/users/social-login/*/native",
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
    @Order(5)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/api/**")
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(ApiKeyAuthFilter(apiKeyProperties), UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(
                JwtAuthenticationFilter(
                    jwtTokenProvider,
                    memberRepository,
                    serverTimeProvider,
                    pendingAllowedPaths = PENDING_ALLOWED_PATHS,
                    suspendedAllowedPaths = SUSPENDED_ALLOWED_PATHS,
                ),
                ApiKeyAuthFilter::class.java,
            )
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .build()
    }

    /**
     * WebSocket(STOMP) 핸드셰이크 — HTTP 계층은 permitAll.
     * 브라우저 WebSocket 은 핸드셰이크에 커스텀 헤더(X-API-Key/Authorization)를 실을 수 없으므로,
     * 실제 인증·인가는 STOMP CONNECT/SUBSCRIBE 프레임에서 `StompAuthChannelInterceptor` 가 수행한다.
     * 배경: ADR 0009.
     */
    @Bean
    @Order(6)
    fun webSocketSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            .securityMatcher("/ws/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    /**
     * 그 외 모든 경로 — 차단
     */
    @Bean
    @Order(7)
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
     * refreshToken을 HttpOnly 쿠키로 주고받으므로 allowCredentials는 true.
     * (allowCredentials=true이면 allowedOrigins에 와일드카드 `*`를 쓸 수 없어 명시적 origin 목록을 사용한다.)
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "X-API-Key")
            allowCredentials = true
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }

    companion object {
        // PENDING(가입 미완료) 회원도 JWT만으로 접근 가능한 경로.
        // - GET /api/v1/users/me: 가입 정보 prefill 조회
        // - POST /api/v1/users: 회원가입 완료(추가 정보 입력) — 호출 시점엔 아직 PENDING이므로 허용 필요
        //
        // ⚠️ JwtAuthenticationFilter는 HTTP method를 무시하고 경로(requestURI)만으로 매칭한다.
        //    같은 경로에 다른 method 엔드포인트(예: GET/DELETE /api/v1/users)를 추가하면
        //    PENDING 회원에게도 함께 열리므로, 그때 PENDING 노출 여부를 반드시 재검토할 것.
        private val PENDING_ALLOWED_PATHS = setOf("/api/v1/users/me", "/api/v1/users")

        // 제재 회원의 유일한 안내 창구 — URI-only 매칭이므로 다른 기능과 경로를 공유하지 말 것.
        private val SUSPENDED_ALLOWED_PATHS = setOf("/api/v1/users/me/sanction")
    }
}
