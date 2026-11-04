package com.ditto.api.admin.featureflag

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 서버 기능 플래그 — 기능별로 켜고 끈다. QA 도구와 달리 실서비스 기능을 여닫는 화면이라 QA 스위치와 무관하게 열린다.
 * 바꾼 값은 이 서버에 곧바로, 다른 서버에는 캐시가 만료되는 대로(최대 1분) 반영된다.
 */
@Controller
class AdminFeatureFlagController(
    private val featureFlagAdminService: FeatureFlagAdminService,
) {

    @GetMapping("/admin/feature-flags")
    fun page(model: Model): String {
        model.addAttribute("flags", featureFlagAdminService.list())
        model.addAttribute("cacheMinutes", FeatureFlags.TTL.toMinutes())
        model.addAttribute("active", "feature-flag")
        return "feature-flags"
    }

    @PostMapping("/admin/feature-flags/{key}")
    fun set(
        @PathVariable key: FeatureFlagKey,
        @RequestParam enabled: Boolean,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        featureFlagAdminService.set(key, enabled, admin.memberId)
        log.info { "어드민[${admin.displayName}] 이 기능 플래그 $key 를 ${if (enabled) "켰다" else "껐다"}" }
        redirectAttributes.addFlashAttribute(
            "message",
            "$key 를 ${if (enabled) "켰습니다" else "껐습니다"}. 다른 서버에는 최대 ${FeatureFlags.TTL.toMinutes()}분 뒤 반영됩니다.",
        )
        return "redirect:/admin/feature-flags"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
