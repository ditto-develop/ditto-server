package com.ditto.api.support

import com.ditto.api.config.auth.MemberPrincipal
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class FakeMemberPrincipalResolver(
    private val memberId: Long = DEFAULT_MEMBER_ID,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType == MemberPrincipal::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        return MemberPrincipal(memberId = memberId)
    }

    companion object {
        const val DEFAULT_MEMBER_ID = 1L
    }
}
