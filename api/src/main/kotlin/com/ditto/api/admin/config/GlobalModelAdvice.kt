package com.ditto.api.admin.config

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.config.AdminQaToolsProperties
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 어드민 뷰가 공통으로 쓰는 값(상단바의 로그인한 어드민, QA 화면 링크를 보일지 정하는 스위치)을 넘긴다.
 * api REST 컨트롤러에는 적용되지 않도록 admin 패키지로 한정한다.
 */
@ControllerAdvice(basePackages = ["com.ditto.api.admin"])
class GlobalModelAdvice(
    private val adminQaToolsProperties: AdminQaToolsProperties,
) {
    @ModelAttribute("admin")
    fun admin(@AuthenticationPrincipal principal: AdminPrincipal?): AdminPrincipal? = principal

    @ModelAttribute("qaToolsEnabled")
    fun qaToolsEnabled(): Boolean = adminQaToolsProperties.enabled
}
