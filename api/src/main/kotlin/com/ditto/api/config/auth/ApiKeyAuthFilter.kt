package com.ditto.api.config.auth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.response.ApiResponse
import com.ditto.common.serialization.ObjectMapperFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class ApiKeyAuthFilter(
    private val apiKeyProperties: ApiKeyProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader(API_KEY_HEADER)

        if (apiKey == null || apiKey != apiKeyProperties.apiKey) {
            // 받은 값은 찍지 않는다 — 오타 한 글자짜리 요청이 오면 유효한 키가 그대로 CloudWatch 에 남는다.
            // 조사에 필요한 건 "헤더가 없었나, 틀렸나"와 어느 경로였나까지다.
            log.warn { "잘못된 api key: ${if (apiKey == null) "헤더 없음" else "불일치"} | ${request.method} ${request.requestURI}" }
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            val unauthorizedResponse = objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.UNAUTHORIZED_ERROR))
            response.writer.write(unauthorizedResponse)
            return
        }

        val authentication = UsernamePasswordAuthenticationToken(
            "api-client",
            null,
            listOf(SimpleGrantedAuthority("ROLE_API")),
        )
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
        private val objectMapper = ObjectMapperFactory.create()
        private val log = KotlinLogging.logger {}
    }
}
