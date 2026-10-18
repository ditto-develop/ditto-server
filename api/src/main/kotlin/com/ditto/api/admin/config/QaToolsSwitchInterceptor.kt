package com.ditto.api.admin.config

import com.ditto.api.config.AdminQaToolsProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.support.RequestContextUtils

/** QA 도구 스위치가 꺼져 있으면 QA 경로 요청을 처리하지 않고 대시보드로 돌려보낸다. */
@Component
class QaToolsSwitchInterceptor(
    private val adminQaToolsProperties: AdminQaToolsProperties,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (adminQaToolsProperties.enabled) return true

        log.warn { "QA 도구가 꺼져 있어 요청을 막음: ${request.method} ${request.requestURI}" }
        RequestContextUtils.getOutputFlashMap(request)["error"] = DISABLED_MESSAGE
        RequestContextUtils.saveOutputFlashMap(DASHBOARD_PATH, request, response)
        response.sendRedirect(request.contextPath + DASHBOARD_PATH)
        return false
    }

    companion object {
        val QA_TOOL_PATHS = listOf(
            "/admin/qa/**",
            "/admin/dummy/**",
            "/admin/time-override/**",
            "/admin/quiz-sets/*/qa/**",
        )
        const val DISABLED_MESSAGE =
            "이 환경에서는 QA 도구가 꺼져 있습니다. 환경변수 ADMIN_QA_TOOLS_ENABLED를 켠 환경에서만 쓸 수 있습니다."
        private const val DASHBOARD_PATH = "/admin"
        private val log = KotlinLogging.logger {}
    }
}
