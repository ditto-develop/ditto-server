package com.ditto.api.launchnotification.scheduler

import com.ditto.domain.launchnotification.entity.LaunchNotificationRequest
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 출시 안내를 보낸 뒤 보유 기간([LaunchNotificationRequest.RETENTION_DAYS]일)이 지난 신청을 지운다.
 * 아직 안내를 안 보낸 신청은 지우지 않는다 — 출시 전까지 들고 있어야 하는 정보다.
 *
 * 실제 시각으로 돈다(어드민 시각 오버라이드를 쓰지 않는다).
 */
@Component
class LaunchNotificationPurgeScheduler(
    private val launchNotificationRequestRepository: LaunchNotificationRequestRepository,
) {

    /** 매일 04:20 — 알림 보관 기간 삭제(04:10) 뒤로 둔다. */
    @Scheduled(cron = "\${launch-notification.purge.scheduler.cron:0 20 4 * * *}")
    fun purgeExpired() {
        val threshold = LaunchNotificationRequest.retentionThreshold(LocalDateTime.now())
        val deleted = launchNotificationRequestRepository.deleteNotifiedBefore(threshold)

        if (deleted > 0) {
            logger.info { "보유 기간 경과 출시 알림 신청 파기: ${deleted}건 (기준 $threshold)" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
