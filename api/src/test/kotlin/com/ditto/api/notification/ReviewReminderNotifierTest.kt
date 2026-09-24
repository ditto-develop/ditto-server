package com.ditto.api.notification

import com.ditto.api.notification.notifier.ReviewReminderNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.repository.MemberReviewRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

// 주말 방은 월요일 00:00 에 끝나고 그때 평가가 열린다. 리마인드는 그 월요일 09:00 에 나간다.
private val WEEKEND_ENDED = LocalDateTime.of(2026, 3, 16, 0, 0)
private val MONDAY_MORNING = LocalDateTime.of(2026, 3, 16, 9, 0)

class ReviewReminderNotifierTest(
    private val reviewReminderNotifier: ReviewReminderNotifier,
    private val memberReviewRepository: MemberReviewRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun savePending(authorMemberId: Long, chatRoomId: Long, availableAt: LocalDateTime = WEEKEND_ENDED) =
        memberReviewRepository.save(
            MemberReviewFixture.create(authorMemberId = authorMemberId, chatRoomId = chatRoomId, availableAt = availableAt),
        )

    "직전 주말에 열린 평가를 끝내지 않은 작성자에게 알린다" - {
        "미완료 작성자만 받는다" {
            savePending(authorMemberId = 1L, chatRoomId = 10L)
            memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = 2L, chatRoomId = 10L, availableAt = WEEKEND_ENDED)
                    .apply { recordAnswer(hasRemainingTarget = false, answeredAt = WEEKEND_ENDED.plusHours(1)) },
            )

            reviewReminderNotifier.notifyPending(MONDAY_MORNING) shouldBe 1

            notificationRepository.findAll().single().let {
                it.memberId shouldBe 1L
                it.type shouldBe NotificationType.REVIEW_REMINDER
                it.title shouldBe "이번 만남은 어떠셨나요?"
                it.body shouldBe "잠깐이면 돼요. 다음 만남을 위해 평가해주세요."
                it.targetId shouldBe 10L
            }
        }

        "방이 여럿이면 방마다 받는다" {
            savePending(authorMemberId = 1L, chatRoomId = 10L)
            savePending(authorMemberId = 1L, chatRoomId = 11L, availableAt = WEEKEND_ENDED.minusDays(1))

            reviewReminderNotifier.notifyPending(MONDAY_MORNING) shouldBe 2

            notificationRepository.findAll().map { it.targetId }.toSet() shouldBe setOf(10L, 11L)
        }

        "7일보다 오래된 미완료 평가는 받지 않는다" {
            savePending(authorMemberId = 1L, chatRoomId = 10L, availableAt = MONDAY_MORNING.minusDays(8))

            reviewReminderNotifier.notifyPending(MONDAY_MORNING) shouldBe 0
        }

        "다시 불러도 방마다 한 번이다" {
            savePending(authorMemberId = 1L, chatRoomId = 10L)
            reviewReminderNotifier.notifyPending(MONDAY_MORNING)

            reviewReminderNotifier.notifyPending(MONDAY_MORNING.plusHours(1)) shouldBe 0

            notificationRepository.count() shouldBe 1
        }
    }
})
