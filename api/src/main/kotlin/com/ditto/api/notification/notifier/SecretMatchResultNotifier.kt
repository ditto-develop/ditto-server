package com.ditto.api.notification.notifier

import com.ditto.api.match.service.SecretRoundResult
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 시크릿 매칭 라운드에서 1:1 후보를 새로 받은 회원에게 알린다. 후보가 없던 회원에게는 보내지 않는다(기획 SM-08).
 *
 * 라운드는 셋마다 한 번만 후보를 저장하므로 이미 돈 라운드의 결과에는 받는 사람이 없다.
 * 배치 트랜잭션 밖에서 부르고, 실패는 삼킨다.
 */
@Component
class SecretMatchResultNotifier(
    private val notificationAppender: NotificationAppender,
) {

    /** 실제로 남긴 알림 수. 실패하면 0 이다. */
    fun notifyFor(roundResults: List<SecretRoundResult>): Int =
        runCatchingExceptions { appendAll(roundResults) }
            .onFailure { logger.warn(it) { "시크릿 매칭 결과 알림 실패 — 무시한다: quizSetIds=${roundResults.map { it.quizSetId }}" } }
            .getOrDefault(0)

    private fun appendAll(roundResults: List<SecretRoundResult>): Int {
        val notified = roundResults.sumOf { result ->
            notificationAppender.appendAll(
                memberIds = result.newCandidateOwnerIds,
                content = NotificationMessages.secretMatchResult(),
                targetId = result.quizSetId,
            )
        }
        if (notified > 0) {
            logger.info { "시크릿 매칭 결과 알림: ${notified}건 (퀴즈셋 ${roundResults.map { it.quizSetId }})" }
        }
        return notified
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
