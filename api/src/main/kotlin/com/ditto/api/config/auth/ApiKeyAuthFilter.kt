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
            log.warn { "잘못된 api key=$apiKey" }
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
