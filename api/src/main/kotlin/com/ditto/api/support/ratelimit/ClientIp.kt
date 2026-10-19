package com.ditto.api.support.ratelimit

import jakarta.servlet.http.HttpServletRequest

/**
 * 요청을 보낸 클라이언트 IP.
 *
 * 서버는 ALB 뒤에 있어 `remoteAddr`가 ALB 주소다. ALB는 `X-Forwarded-For` **끝에** 자기가 본 클라이언트 IP를 붙인다.
 * 앞쪽 값은 클라이언트가 마음대로 넣을 수 있으므로 맨 끝 값을 쓴다.
 * 헤더가 없으면(로컬·테스트, ALB를 거치지 않은 호출) `remoteAddr`를 쓴다.
 */
object ClientIp {

    private const val FORWARDED_FOR = "X-Forwarded-For"

    fun of(request: HttpServletRequest): String =
        request.getHeader(FORWARDED_FOR)
            ?.split(',')
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotEmpty() }
            ?: request.remoteAddr
}
