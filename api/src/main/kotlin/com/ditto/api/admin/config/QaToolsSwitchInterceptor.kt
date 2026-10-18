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

        log.info { "QA 도구가 꺼져 있어 요청을 막음: ${request.method} ${request.requestURI}" }
        redirectToDashboardWithNotice(request, response)
        return false
    }

    private fun redirectToDashboardWithNotice(request: HttpServletRequest, response: HttpServletResponse) {
        RequestContextUtils.getOutputFlashMap(request)["error"] = DISABLED_MESSAGE
        RequestContextUtils.saveOutputFlashMap(DASHBOARD_PATH, request, response)
        response.sendRedirect(request.contextPath + DASHBOARD_PATH)
    }

    companion object {
        // 매칭 실행은 배치가 실패했을 때 손으로 다시 돌리는 운영 수단이라 넣지 않는다.
        val QA_TOOL_PATHS = listOf(
            "/admin/qa/**",
            "/admin/dummy/**",
            "/admin/time-override/**",
            "/admin/quiz-sets/*/qa/**",
        )
        private const val DISABLED_MESSAGE =
            "이 서버에서는 QA 도구(더미·QA 콘솔·시간 조정·퀴즈셋 초기화)가 꺼져 있습니다. " +
                "필요하면 서버 담당자에게 ADMIN_QA_TOOLS_ENABLED 켜기를 요청하세요."
        private const val DASHBOARD_PATH = "/admin"
        private val log = KotlinLogging.logger {}
    }
}
