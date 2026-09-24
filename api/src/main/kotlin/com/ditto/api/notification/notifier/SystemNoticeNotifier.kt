package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.notification.entity.SystemNotice
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 시스템 공지를 [memberIds]에게 적재한다. 어드민 요청 안에서 동기로 끝난다(다른 전원 알림과 같다).
 * 대상 조회는 호출자(SystemNoticeFacade)가 이력을 남기기 전에 한다. 조회가 실패하면 이력도 남지 않아야 해서다.
 * 회원 한 명의 적재 실패는 NotificationAppender 가 삼키고 로그로 남는다. 자동 재시도는 없다.
 */
@Component
class SystemNoticeNotifier(
    private val notificationAppender: NotificationAppender,
) {
    /** 실제로 남긴 알림 수. */
    fun notifyPublished(notice: SystemNotice, memberIds: Collection<Long>): Int {
        val appended = notificationAppender.appendAll(
            memberIds = memberIds,
            content = NotificationMessages.systemNotice(notice.title, notice.body),
            targetId = notice.id,
        )
        logger.info { "시스템 공지 발송: ${appended}건 / 대상 ${memberIds.size}명 (noticeId=${notice.id})" }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
