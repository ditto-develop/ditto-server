package com.ditto.api.notification.facade

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.notice.AdminNoticeService
import com.ditto.api.notification.notifier.SystemNoticeNotifier
import com.ditto.domain.notification.entity.SystemNotice
import org.springframework.stereotype.Component

/**
 * 시스템 공지 발송 진입점. 트랜잭션을 열지 않는다 — 알림 적재는 요청 경로 규칙대로 이력 커밋 뒤에
 * 트랜잭션 밖에서 돈다(ADR 0018). 이력 저장이 실패하면 발송하지 않고, 발송이 부분 실패하면 실제 수만 기록한다.
 */
@Component
class SystemNoticeFacade(
    private val adminNoticeService: AdminNoticeService,
    private val systemNoticeNotifier: SystemNoticeNotifier,
) {

    /** 이력을 남기고 활성 회원 전원에게 보낸다. 수신 수가 기록된 이력을 돌려준다. */
    fun publish(title: String, body: String?, author: AdminPrincipal): SystemNotice {
        val notice = adminNoticeService.record(title, body, author)
        val recipientCount = systemNoticeNotifier.notifyPublished(notice)
        return adminNoticeService.recordRecipientCount(notice.id, recipientCount)
    }
}
