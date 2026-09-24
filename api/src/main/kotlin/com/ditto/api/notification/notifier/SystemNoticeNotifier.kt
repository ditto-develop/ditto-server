package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.SystemNotice
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 시스템 공지를 활성 회원 전원에게 적재한다. 어드민 요청 안에서 동기로 끝난다(다른 전원 알림과 같다).
 * 회원 한 명의 적재 실패는 NotificationAppender 가 삼키고 로그로 남는다. 자동 재시도는 없다.
 */
@Component
class SystemNoticeNotifier(
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 실제로 남긴 알림 수. 회원 조회가 실패하면 0. */
    fun notifyPublished(notice: SystemNotice): Int =
        runCatchingExceptions { appendToActiveMembers(notice) }
            .onFailure { logger.warn(it) { "시스템 공지 발송 실패 — 무시한다: noticeId=${notice.id}" } }
            .getOrDefault(0)

    private fun appendToActiveMembers(notice: SystemNotice): Int {
        val appended = notificationAppender.appendAll(
            memberIds = memberRepository.findAllIdsByStatus(MemberStatus.ACTIVE),
            content = NotificationMessages.systemNotice(notice.title, notice.body),
            targetId = notice.id,
        )
        logger.info { "시스템 공지 발송: ${appended}건 (noticeId=${notice.id})" }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
