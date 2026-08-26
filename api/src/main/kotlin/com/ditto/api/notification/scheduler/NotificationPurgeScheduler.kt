package com.ditto.api.notification.scheduler

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.repository.NotificationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 보관 기간이 지난 알림을 지운다.
 *
 * 조회가 30일로 자르므로([Notification.RETENTION_DAYS]) 지워지는 행은 이미 아무에게도 보이지 않는다.
 * 그래도 지우는 이유는 알림 본문에 닉네임·메시지 미리보기가 들어 있어서다 — 보이지 않는 개인정보를
 * 무한히 들고 있을 이유가 없다.
 *
 * 실제 시각으로 돈다(어드민 시각 오버라이드를 쓰지 않는다) — 기준이 되는 `created_at`이 실제 시각이다.
 */
@Component
class NotificationPurgeScheduler(
    private val notificationRepository: NotificationRepository,
) {

    /** 매일 04:10 — 탈퇴 완전 삭제(04:00) 뒤로 둔다. 트래픽이 가장 낮은 시간대다. */
    @Scheduled(cron = "\${notification.purge.scheduler.cron:0 10 4 * * *}")
    fun purgeExpired() {
        val threshold = Notification.retentionFrom()
        val deleted = notificationRepository.deleteCreatedBefore(threshold)

        if (deleted > 0) {
            logger.info { "보관 기간 경과 알림 삭제: ${deleted}건 (기준 $threshold)" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
