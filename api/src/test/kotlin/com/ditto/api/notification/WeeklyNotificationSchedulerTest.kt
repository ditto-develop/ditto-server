package com.ditto.api.notification

import com.ditto.api.notification.notifier.QuizNotifier
import com.ditto.api.notification.scheduler.WeeklyNotificationScheduler
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime

class WeeklyNotificationSchedulerTest : FreeSpec({
    val quizNotifier = mockk<QuizNotifier>(relaxed = true)
    val scheduler = WeeklyNotificationScheduler(quizNotifier)

    "퀴즈 오픈 알림은 실제 시각으로 부른다" {
        val at = slot<LocalDateTime>()

        scheduler.notifyQuizOpened()

        verify { quizNotifier.notifyOpened(capture(at)) }
        (at.captured > LocalDateTime.of(2026, 1, 1, 0, 0)) shouldBe true
    }

    "퀴즈 마감 임박 알림은 실제 시각으로 부른다" {
        val at = slot<LocalDateTime>()

        scheduler.notifyQuizClosingSoon()

        verify { quizNotifier.notifyClosingSoon(capture(at)) }
        (at.captured > LocalDateTime.of(2026, 1, 1, 0, 0)) shouldBe true
    }
})
