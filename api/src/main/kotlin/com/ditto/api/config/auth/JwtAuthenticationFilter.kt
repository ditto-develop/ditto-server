package com.ditto.api.config.auth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.response.ApiResponse
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.member.repository.MemberRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import kotlin.jvm.optionals.getOrNull

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberRepository: MemberRepository,
    private val pendingAllowedPaths: Set<String> = emptySet(),
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)

        if (token == null || !jwtTokenProvider.isValid(token)) {
            sendError(response, ErrorCode.UNAUTHORIZED_ERROR)
            return
        }

        val memberId = jwtTokenProvider.getMemberId(token)
        val member = memberRepository.findById(memberId).getOrNull()
            ?: run {
                sendError(response, ErrorCode.UNAUTHORIZED_ERROR)
                return
            }

        // 회원가입 미완료(PENDING) 회원은 보호 API 접근을 차단한다. (허용 경로는 예외)
        if (member.isPending() && request.requestURI !in pendingAllowedPaths) {
            sendError(response, ErrorCode.SIGNUP_REQUIRED)
            return
        }

        if (request.requestURI.startsWith(ADMIN_PATH_PREFIX) && !member.isAdmin()) {
            sendError(response, ErrorCode.FORBIDDEN)
            return
        }

        setAuthentication(memberId)

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request
            .getHeader(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.substring(BEARER_PREFIX.length)

    private fun sendError(
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        response.status = errorCode.status
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)))
    }

    private fun setAuthentication(memberId: Long) {
        val principal = MemberPrincipal(memberId = memberId)

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
        private const val ADMIN_PATH_PREFIX = "/api/v1/admin"
        private val objectMapper = ObjectMapperFactory.create()
    }
}
