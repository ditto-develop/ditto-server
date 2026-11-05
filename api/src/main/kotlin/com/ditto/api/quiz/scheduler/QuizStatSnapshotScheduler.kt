package com.ditto.api.quiz.scheduler

import com.ditto.api.config.SchedulerLockDurations
import com.ditto.api.quiz.stat.QuizStatSnapshotService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 마감된 퀴즈셋의 통계 스냅샷을 만든다. 응답 마감(수 23:59:59) 직후인 목 00:00 에 돌고, 매칭 배치(00:01)보다 앞선다.
 *
 * 실제 시각을 쓴다 — 매칭 배치와 같은 이유다(어드민 시각 오버라이드로 옮긴 주의 셋은 조회 시 지연 생성된다).
 * 멱등이라 겹치거나 빠져도 결과가 같다.
 */
@Component
class QuizStatSnapshotScheduler(
    private val quizStatSnapshotService: QuizStatSnapshotService,
) {

    @Scheduled(cron = "\${quiz.stat-snapshot.scheduler.cron:0 0 0 * * THU}")
    @SchedulerLock(
        name = "quiz-stat-snapshot",
        lockAtMostFor = SchedulerLockDurations.WEEKLY_AT_MOST,
        lockAtLeastFor = SchedulerLockDurations.AT_LEAST,
    )
    fun snapshot() {
        quizStatSnapshotService.snapshotEnded(LocalDateTime.now())
    }
}
