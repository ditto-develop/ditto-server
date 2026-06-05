package com.ditto.api.match.scheduler

import com.ditto.api.match.service.MatchmakingService
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 마감된 퀴즈셋의 매칭 후보를 주기적으로 생성하는 스케줄러.
 *
 * 마감됐고 아직 후보가 없는 퀴즈셋만(anti-join) 단일 쿼리로 조회해 처리한다(멱등).
 * 주기는 `matching.scheduler.cron` 프로퍼티로 조정하며 기본값은 매일 05:00 이다.
 */
@Component
class MatchingScheduler(
    private val quizSetRepository: QuizSetRepository,
    private val matchmakingService: MatchmakingService,
) {

    @Scheduled(cron = "\${matching.scheduler.cron:0 0 5 * * *}")
    fun generateForEndedQuizSets() {
        quizSetRepository
            .findEndedQuizSetsWithoutCandidates(LocalDateTime.now())
            .forEach { matchmakingService.generateMatchingCandidates(it.id) }
    }
}
