package com.ditto.api.admin.launchnotification

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.domain.launchnotification.entity.LaunchNotificationRequest
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 출시 알림 신청 관리. 운영 흐름: CSV 내보내기 → 메일 발송(사람이 직접) → 발송 완료 처리 → 30일 뒤 자동 파기.
 *
 * 내보내기와 발송 완료는 화면을 열 때의 마지막 미발송 id(`upToId`)를 함께 쓴다 — 그 사이 새로 들어온 신청이
 * 메일을 못 받았는데 "보냄"으로 묻히지 않게.
 * 발송 시각은 파기 기준이라 실제 시각을 쓴다(어드민 시각 오버라이드를 따르지 않는다).
 */
@Controller
class AdminLaunchNotificationController(
    private val adminLaunchNotificationService: AdminLaunchNotificationService,
) {

    @GetMapping("/admin/launch-notifications")
    fun summary(@RequestParam(required = false) platform: LaunchPlatform?, model: Model): String {
        model.addAttribute("summary", adminLaunchNotificationService.summary(platform ?: LaunchPlatform.ANDROID))
        model.addAttribute("platforms", LaunchPlatform.entries)
        model.addAttribute("retentionDays", LaunchNotificationRequest.RETENTION_DAYS)
        model.addAttribute("active", "launch-notification")
        return "launch-notification/summary"
    }

    @GetMapping("/admin/launch-notifications/export")
    fun export(
        @RequestParam platform: LaunchPlatform,
        @RequestParam upToId: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
    ): ResponseEntity<ByteArray> {
        val csv = adminLaunchNotificationService.exportPendingCsv(platform, upToId)
        // 누가 언제 이메일 목록을 꺼냈는지 남긴다. 이메일 자체는 남기지 않는다.
        log.info { "어드민[${admin.displayName}] 이 출시 알림 신청(${platform.name}, #$upToId 이하 미발송)을 내보냄" }
        val filename = "launch-notification-${platform.name.lowercase()}-upto-$upToId.csv"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toByteArray(StandardCharsets.UTF_8))
    }

    @PostMapping("/admin/launch-notifications/mark-notified")
    fun markNotified(
        @RequestParam platform: LaunchPlatform,
        @RequestParam upToId: Long,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        val marked = adminLaunchNotificationService.markNotified(platform, upToId, now)
        log.info { "어드민[${admin.displayName}] 이 출시 알림 신청 ${marked}건(${platform.name}, #$upToId 이하)을 발송 완료 처리" }
        redirectAttributes.addFlashAttribute("message", "${marked}건을 발송 완료로 처리했습니다.")
        return "redirect:/admin/launch-notifications?platform=${platform.name}"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
