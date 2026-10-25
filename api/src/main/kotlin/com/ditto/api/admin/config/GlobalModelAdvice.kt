package com.ditto.api.admin.config

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.api.system.ServerTimeService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.time.LocalDateTime

/**
 * 어드민 뷰 공통 모델 값(로그인한 어드민, QA 도구 스위치, 시간 조정 시각).
 * api REST 컨트롤러에는 적용되지 않도록 admin 패키지로 한정한다.
 */
@ControllerAdvice(basePackages = ["com.ditto.api.admin"])
class GlobalModelAdvice(
    private val adminQaToolsProperties: AdminQaToolsProperties,
    private val serverTimeService: ServerTimeService,
) {
    @ModelAttribute("admin")
    fun admin(@AuthenticationPrincipal principal: AdminPrincipal?): AdminPrincipal? = principal

    @ModelAttribute("qaToolsEnabled")
    fun qaToolsEnabled(): Boolean = adminQaToolsProperties.enabled

    // 스위치가 꺼져 있으면 오버라이드 행이 남아 있어도 실제 시각을 쓰므로 보이지 않는다.
    @ModelAttribute("timeOverrideAt")
    fun timeOverrideAt(): LocalDateTime? {
        if (!adminQaToolsProperties.enabled) return null
        return serverTimeService.getOverride().takeIf { it.enabled }?.overrideDateTime
    }
}
