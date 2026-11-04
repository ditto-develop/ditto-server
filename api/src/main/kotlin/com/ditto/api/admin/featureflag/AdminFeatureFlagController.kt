package com.ditto.api.admin.featureflag

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.system.ServerTimeProvider
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
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/admin/feature-flags")
    fun page(model: Model): String {
        model.addAttribute("flags", featureFlagAdminService.list())
        model.addAttribute("cacheMinutes", CACHE_MINUTES)
        // 켠 시각이 지금보다 뒤면 QA 가 시각을 과거로 돌려 둔 것이다. 화면이 경고한다.
        model.addAttribute("serverNow", serverTimeProvider.now())
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
            "${key.description}($key)을(를) ${if (enabled) "켰습니다" else "껐습니다"}. 최대 ${CACHE_MINUTES}분 뒤 모든 서버에 적용됩니다.",
        )
        return "redirect:/admin/feature-flags"
    }

    companion object {
        private val log = KotlinLogging.logger {}

        /** 다른 서버에 퍼지기까지 걸리는 최대 시간(분). 캐시 TTL 이 1분 미만이어도 화면에는 1분으로 보인다. */
        private val CACHE_MINUTES: Long = FeatureFlags.TTL.toMinutes().coerceAtLeast(1)
    }
}
