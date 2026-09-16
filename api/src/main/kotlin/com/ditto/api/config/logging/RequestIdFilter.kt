package com.ditto.api.config.logging

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : Filter {
    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_MDC_KEY = "requestId"

        /** 인증에 성공한 요청에만 실린다 — [com.ditto.api.config.auth.JwtAuthenticationFilter] 가 넣는다. */
        const val MEMBER_ID_MDC_KEY = "memberId"
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val requestId = httpRequest.getHeader(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString().substring(0, 8)
        try {
            MDC.put(REQUEST_ID_MDC_KEY, requestId)
            chain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY)
            // 인증 필터가 넣은 값도 여기서 지운다 — 톰캣이 스레드를 재사용하므로 남겨 두면
            // 다음(익명일 수도 있는) 요청 로그에 엉뚱한 회원이 붙는다.
            MDC.remove(MEMBER_ID_MDC_KEY)
        }
    }
}
