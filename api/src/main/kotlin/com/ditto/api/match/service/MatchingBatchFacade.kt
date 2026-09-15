package com.ditto.api.match.service

import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 마감된 퀴즈셋의 매칭 후보를 일괄 생성하는 배치 진입점. 실시간 스케줄러와 어드민 수동 실행이 공유한다.
 *
 * **이 클래스는 트랜잭션을 열지 않는다 — 그것이 격리의 근거다.** 퀴즈셋 하나의 트랜잭션은
 * [MatchmakingService.generateMatchingCandidates]가 열고, 여기서 프록시를 거쳐 부르므로 셋마다 독립된 트랜잭션이 된다.
 * 한 셋이 실패해도 나머지 셋의 후보는 그대로 커밋된다. 이 클래스나 호출자에 `@Transactional`을 붙이면 셋 트랜잭션이
 * 거기에 합류해 격리가 깨진다. `REQUIRES_NEW`로 강제하지 않는 이유: 어드민 단건 재생성 경로와 `@Transactional`
 * 테스트가 같은 메서드를 바깥 트랜잭션 안에서 쓰는데, 그때 바깥의 미커밋 데이터를 못 보게 된다.
 * 배치 로직을 [MatchmakingService] 안에 두면 자기 호출이 프록시를 거치지 않아 이 격리를 만들 수 없다. 배경: ADR 0027.
 */
@Component
class MatchingBatchFacade(
    private val quizSetRepository: QuizSetRepository,
    private val matchmakingService: MatchmakingService,
    private val sanctionExpiryService: SanctionExpiryService,
) {

    /**
     * 마감([now] 기준)됐고 아직 후보가 없는 퀴즈셋의 매칭 후보를 생성한다(멱등).
     * 실패한 셋은 경고 로그만 남기고 다음 셋으로 넘어간다 — 후보가 없는 채로 남아 다음 배치가 다시 집는다.
     *
     * @return 이번 호출로 후보를 생성한 퀴즈셋 ID(실패한 셋 제외). 알림은 커밋된 뒤에 남겨야 하므로
     *   (`MatchResultNotifier`) 대상 목록을 여기서 흘려보낸다.
     */
    fun runScheduledMatching(now: LocalDateTime): List<Long> {
        // 만료 정지 원복을 후보 생성보다 먼저 — 원복된 회원이 이번 매칭 대상에 포함되게 한다 (ADR 0009).
        sanctionExpiryService.expireDue(now)

        return quizSetRepository
            .findEndedQuizSetsWithoutCandidates(now)
            .mapNotNull { quizSet -> generatedQuizSetIdOrNull(quizSet.id) }
    }

    /** 후보를 생성하고 그 퀴즈셋 ID를 돌려준다. 실패하면 경고 로그만 남기고 null. */
    private fun generatedQuizSetIdOrNull(quizSetId: Long): Long? =
        runCatchingExceptions { matchmakingService.generateMatchingCandidates(quizSetId) }
            .onFailure { logger.warn(it) { "매칭 후보 생성 실패 — 이 퀴즈셋만 건너뛰고 계속한다: quizSetId=$quizSetId" } }
            .map { quizSetId }
            .getOrNull()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
