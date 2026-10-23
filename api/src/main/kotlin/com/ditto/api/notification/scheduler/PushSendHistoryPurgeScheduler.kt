package com.ditto.api.notification.scheduler

import com.ditto.domain.notification.entity.PushSendHistory
import com.ditto.domain.notification.repository.PushSendHistoryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 보관 기간(180일)이 지난 푸시 발송 이력을 지운다.
 *
 * 실제 시각으로 돈다(어드민 시각 오버라이드를 쓰지 않는다). 기준이 되는 `created_at`이 실제 시각이다.
 */
@Component
class PushSendHistoryPurgeScheduler(
    private val pushSendHistoryRepository: PushSendHistoryRepository,
) {

    /** 매일 04:30. 출시 알림 신청 파기(04:20) 뒤에 돈다. */
    @Scheduled(cron = "\${push-send-history.purge.scheduler.cron:0 30 4 * * *}")
    fun purgeExpired() {
        val threshold = PushSendHistory.retentionFrom()
        val deleted = pushSendHistoryRepository.deleteCreatedBefore(threshold)

        if (deleted > 0) {
            logger.info { "보관 기간 경과 푸시 발송 이력 삭제: ${deleted}건 (기준 $threshold)" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
