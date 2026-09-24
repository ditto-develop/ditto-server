package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.review.repository.MemberReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 직전 주말에 끝난 방의 평가를 아직 끝내지 않은 작성자에게 다시 권한다. WeeklyNotificationScheduler 가 월요일에 부른다.
 *
 * 평가에는 마감이 없다. 그래서 "마감 전 리마인드"가 아니라 최근 7일 안에 평가가 열린 것만 한 번 독촉하고,
 * 제출은 계속 열어 둔다. 창을 두지 않으면 몇 달 전 미완료 평가까지 매주 딸려 나온다.
 *
 * 평가 한 건이 (작성자, 방)이라 알림은 방마다 한 번(target_id = chat_room.id)이다. 재매칭 방은 평가가 없어
 * 자연히 빠진다. REVIEW_REQUEST 와 유형이 다른 이유: 그쪽은 ONCE_PER_TARGET 이라 같은 방으로 다시 적재되지 않는다.
 */
@Component
class ReviewReminderNotifier(
    private val memberReviewRepository: MemberReviewRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 조회 실패는 여기서 삼킨다. 실패하면 0. */
    fun notifyPending(now: LocalDateTime): Int =
        runCatchingExceptions { appendToPendingAuthors(now) }
            .onFailure { logger.warn(it) { "평가 리마인드 실패 — 무시한다: now=$now" } }
            .getOrDefault(0)

    private fun appendToPendingAuthors(now: LocalDateTime): Int {
        val pendingReviews = memberReviewRepository.findPendingAvailableBetween(now.minusDays(WINDOW_DAYS), now)
        if (pendingReviews.isEmpty()) {
            return 0
        }

        val content = NotificationMessages.reviewReminder()
        val appended = pendingReviews
            .groupBy { it.chatRoomId }
            .entries
            .sumOf { (chatRoomId, reviews) ->
                notificationAppender.appendAll(reviews.map { it.authorMemberId }, content, targetId = chatRoomId)
            }

        if (appended > 0) {
            logger.info { "평가 리마인드: ${appended}건 (미완료 평가 ${pendingReviews.size}건)" }
        }
        return appended
    }

    companion object {
        private const val WINDOW_DAYS = 7L
        private val logger = KotlinLogging.logger {}
    }
}
