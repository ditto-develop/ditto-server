package com.ditto.api.match.scheduler

import com.ditto.application.match.service.MatchmakingService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 마감된 퀴즈셋의 매칭 후보를 주기적으로 생성하는 스케줄러.
 *
 * 마감됐고 아직 후보가 없는 퀴즈셋만(anti-join) 처리한다(멱등). 배치 로직은 [MatchmakingService.runScheduledMatching]
 * 에 있으며 어드민 수동 실행과 공유한다. 스케줄러는 의도적으로 실제 시각([LocalDateTime.now])을 사용한다.
 * 주기는 `matching.scheduler.cron` 프로퍼티로 조정하며 기본값은 매주 목요일 05:00 이다.
 */
@Component
class MatchingScheduler(
    private val matchmakingService: MatchmakingService,
) {

    @Scheduled(cron = "\${matching.scheduler.cron:0 0 5 * * THU}")
    fun generateForEndedQuizSets() {
        matchmakingService.runScheduledMatching(LocalDateTime.now())
    }
}
