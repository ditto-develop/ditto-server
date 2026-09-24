package com.ditto.api.notification.scheduler

import com.ditto.api.notification.notifier.QuizOpenedNotifier
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 요일에 맞춰 나가는 알림. 사건이 일어나는 코드 지점이 없어(퀴즈는 시각이 되면 그냥 열린다) 시각이 트리거다.
 * MatchingScheduler 와 같이 실제 시각을 쓴다. cron 은 프로퍼티로 바꿀 수 있다.
 */
@Component
class WeeklyNotificationScheduler(
    private val quizOpenedNotifier: QuizOpenedNotifier,
) {

    /** 퀴즈 응답 기간은 월요일 00:00 에 시작한다(QuizResponsePeriod). */
    @Scheduled(cron = "\${notification.quiz-opened.cron:0 0 0 * * MON}")
    fun notifyQuizOpened() {
        quizOpenedNotifier.notifyOpened(LocalDateTime.now())
    }
}
