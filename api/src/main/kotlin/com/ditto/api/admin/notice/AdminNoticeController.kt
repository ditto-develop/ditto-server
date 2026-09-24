package com.ditto.api.admin.notice

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.notification.facade.SystemNoticeFacade
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** 시스템 공지 — 활성 회원 전원에게 SYSTEM_NOTICE 알림을 보내고 이력을 본다. */
@Controller
class AdminNoticeController(
    private val systemNoticeFacade: SystemNoticeFacade,
    private val adminNoticeService: AdminNoticeService,
) {

    @GetMapping("/admin/notices")
    fun page(model: Model): String {
        model.addAttribute("notices", adminNoticeService.history())
        model.addAttribute("titleMaxLength", Notification.TITLE_MAX_LENGTH)
        model.addAttribute("bodyMaxLength", Notification.BODY_MAX_LENGTH)
        model.addAttribute("active", "notice")
        return "notice/list"
    }

    /** 발송은 요청 안에서 끝난다. 회원 수만큼 적재가 돌아 시간이 걸릴 수 있다. */
    @PostMapping("/admin/notices")
    fun publish(
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
        @AuthenticationPrincipal admin: AdminPrincipal,
        redirectAttributes: RedirectAttributes,
    ): String {
        runCatching { systemNoticeFacade.publish(title, body, admin) }
            .onSuccess { notice ->
                log.info { "어드민[${admin.displayName}] 이 시스템 공지 #${notice.id} 발송 — 대상 ${notice.targetCount}명 중 ${notice.recipientCount}명" }
                redirectAttributes.addFlashAttribute("message", "대상 ${notice.targetCount}명 중 ${notice.recipientCount}명에게 공지를 보냈습니다.")
            }
            .onFailure { e ->
                if (e !is WarnException) throw e
                redirectAttributes.addFlashAttribute("error", e.message)
                // 검증에 걸린 입력을 폼에 다시 채워 준다. 리다이렉트로 돌아가면 작성 내용이 사라진다.
                redirectAttributes.addFlashAttribute("title", title)
                redirectAttributes.addFlashAttribute("body", body)
            }
        return "redirect:/admin/notices"
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
