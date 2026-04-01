package com.ditto.api.config.auth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.response.ApiResponse
import com.ditto.common.serialization.ObjectMapperFactory
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)

        if (token == null || !jwtTokenProvider.isValid(token)) {
            retrieveUnauthorized(response)
            return
        }

        setAuthentication(token)

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request
            .getHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.substring(BEARER_PREFIX.length)

    private fun retrieveUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.UNAUTHORIZED_ERROR)))
    }

    private fun setAuthentication(token: String) {
        val principal =
            MemberPrincipal(
                providerUserId = jwtTokenProvider.getProviderUserId(token),
                provider = jwtTokenProvider.getProvider(token),
            )

        val authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                SecurityContextHolder.getContext().authentication?.authorities ?: emptyList(),
            )
        SecurityContextHolder.getContext().authentication = authentication
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private val objectMapper = ObjectMapperFactory.create()
    }
}
