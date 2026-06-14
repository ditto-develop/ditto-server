package com.ditto.admin.config

import com.ditto.admin.auth.AdminPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** 모든 뷰에 로그인한 어드민 정보를 노출(레이아웃 상단바·로그아웃 표시용). */
@ControllerAdvice
class GlobalModelAdvice {
    @ModelAttribute("admin")
    fun admin(@AuthenticationPrincipal principal: AdminPrincipal?): AdminPrincipal? = principal
}
