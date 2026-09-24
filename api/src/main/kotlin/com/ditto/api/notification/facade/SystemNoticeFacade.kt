package com.ditto.api.notification.facade

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.notice.AdminNoticeService
import com.ditto.api.notification.notifier.SystemNoticeNotifier
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.SystemNotice
import org.springframework.stereotype.Component

/**
 * 시스템 공지 발송 진입점. 트랜잭션을 열지 않는다 — 알림 적재는 요청 경로 규칙대로 이력 커밋 뒤에
 * 트랜잭션 밖에서 돈다(ADR 0018).
 *
 * 순서가 중요하다. 대상 조회 → 이력(대상 수 포함) 커밋 → 적재 → 수신 수 기록. 조회를 먼저 하는 이유는 조회가
 * 실패하면 이력 없이 오류로 끝나야 하기 때문이고(사람이 결과를 보는 경로라 삼키지 않는다), 이력을 적재 앞에
 * 커밋하는 이유는 발송이 길어져 화면이 먼저 돌아와도 "발송 중"으로 보여야 하기 때문이다.
 */
@Component
class SystemNoticeFacade(
    private val memberRepository: MemberRepository,
    private val adminNoticeService: AdminNoticeService,
    private val systemNoticeNotifier: SystemNoticeNotifier,
) {

    /** 활성 회원 전원에게 보낸다. 수신 수가 기록된 이력을 돌려준다. */
    fun publish(title: String, body: String?, author: AdminPrincipal): SystemNotice {
        val memberIds = memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE)
        val notice = adminNoticeService.record(title, body, author, targetCount = memberIds.size)
        val recipientCount = systemNoticeNotifier.notifyPublished(notice, memberIds)
        return adminNoticeService.recordRecipientCount(notice.id, recipientCount)
    }
}
