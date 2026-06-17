package com.ditto.api.admin.config

import com.ditto.api.admin.auth.AdminPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 어드민 뷰에만 로그인한 어드민 정보를 노출(레이아웃 상단바·로그아웃 표시용).
 * api REST 컨트롤러에는 적용되지 않도록 admin 패키지로 한정한다.
 */
@ControllerAdvice(basePackages = ["com.ditto.api.admin"])
class GlobalModelAdvice {
    @ModelAttribute("admin")
    fun admin(@AuthenticationPrincipal principal: AdminPrincipal?): AdminPrincipal? = principal
}
