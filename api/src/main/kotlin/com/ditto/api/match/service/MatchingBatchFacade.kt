package com.ditto.api.match.service

import com.ditto.api.match.SecretMatchingSchedule
import com.ditto.api.sanction.service.SanctionExpiryService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
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
    private val featureFlags: FeatureFlags,
) {

    /**
     * 최근 [RETRY_WINDOW_DAYS]일 안에 마감([now] 기준)됐고 아직 후보가 없는 퀴즈셋의 매칭 후보를 생성한다(멱등).
     * 실패한 셋은 경고 로그만 남기고 다음 셋으로 넘어간다 — 후보가 없는 채로 남아 다음 배치가 다시 집는다.
     * 창을 두는 이유: 후보가 0건으로 끝난 셋(완주자 1명 등)은 영원히 후보가 없어서, 하한이 없으면 매주 다시
     * 계산되고 그때마다 노매칭 알림 존재 검사를 다시 탄다. 알림 보관 30일보다 짧아야 한다.
     *
     * @return 이번 호출로 후보를 생성한 퀴즈셋 ID(실패한 셋 제외). 알림은 커밋된 뒤에 남겨야 하므로
     *   (`MatchResultNotifier`) 대상 목록을 여기서 흘려보낸다.
     */
    fun runScheduledMatching(now: LocalDateTime): List<Long> = matchEndedQuizSets(now).generatedQuizSetIds

    /**
     * [now]에 시각이 지난 가장 늦은 시크릿 매칭 라운드 하나를 그 주 활성 1:1 셋마다 돌린다. 놓친 1차는 메우지 않는다.
     * 라운드 전에 정규 배치가 놓친 셋을 먼저 메우고, 정규가 이번에도 실패한 셋은 건너뛴다(ADR 0054).
     */
    fun runSecretRound(now: LocalDateTime): SecretRoundBatchResult {
        if (!featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)) {
            logger.info { "시크릿 매칭 배치: now=$now 플래그 꺼짐" }
            return SecretRoundBatchResult.FLAG_OFF
        }
        val (week, secretRound) = SecretMatchingSchedule.dueRoundAt(now) ?: run {
            logger.info { "시크릿 매칭 배치: now=$now 돌 차례인 라운드 없음" }
            return SecretRoundBatchResult.NO_ROUND_DUE
        }

        val regularOutcome = matchEndedQuizSets(now)
        val roundTargetIds = quizSetRepository.findByWeekStartedOn(week.startedOn)
            .filter { it.isActive && it.matchingType == MatchingType.ONE_TO_ONE }
            .map { it.id }
            .filter { it !in regularOutcome.failedQuizSetIds }
        val resultsByQuizSetId = roundTargetIds.associateWith { secretRoundResultOrNull(it, secretRound) }
        val result = SecretRoundBatchResult(
            status = SecretRoundBatchResult.Status.RAN,
            secretRound = secretRound,
            backfilledRegularQuizSetIds = regularOutcome.generatedQuizSetIds,
            roundResults = resultsByQuizSetId.values.filterNotNull(),
            failedQuizSetIds = (regularOutcome.failedQuizSetIds + resultsByQuizSetId.filterValues { it == null }.keys).distinct(),
        )
        logSecretRoundBatch(now, result)
        return result
    }

    // 만료 정지 원복을 후보 생성보다 먼저 한다. 원복된 회원이 이번 매칭 대상에 포함되게 한다 (ADR 0009).
    private fun matchEndedQuizSets(now: LocalDateTime): RegularBatchOutcome {
        sanctionExpiryService.expireDue(now)

        val (generated, failed) = quizSetRepository
            .findEndedQuizSetsWithoutCandidates(now.minusDays(RETRY_WINDOW_DAYS), now)
            .map { quizSet -> quizSet.id to isGenerated(quizSet.id) }
            .partition { (_, succeeded) -> succeeded }
        return RegularBatchOutcome(generated.map { it.first }, failed.map { it.first })
    }

    /** 후보를 생성하면 true. 실패하면 경고 로그만 남기고 false. */
    private fun isGenerated(quizSetId: Long): Boolean =
        runCatchingExceptions { matchmakingService.generateMatchingCandidates(quizSetId) }
            .onFailure { logger.warn(it) { "매칭 후보 생성 실패 — 이 퀴즈셋만 건너뛰고 계속한다: quizSetId=$quizSetId" } }
            .isSuccess

    private fun secretRoundResultOrNull(quizSetId: Long, secretRound: CandidateOrigin): SecretRoundResult? =
        runCatchingExceptions { matchmakingService.generateSecretRoundCandidates(quizSetId, secretRound) }
            .onFailure { logger.warn(it) { "시크릿 매칭 실패, 이 퀴즈셋만 건너뛴다: quizSetId=$quizSetId round=$secretRound" } }
            .getOrNull()

    private fun logSecretRoundBatch(now: LocalDateTime, result: SecretRoundBatchResult) {
        val (skipped, generated) = result.roundResults.partition { it.alreadyGenerated }
        logger.info {
            "시크릿 매칭 배치: now=$now round=${result.secretRound} 정규메움=${result.backfilledRegularQuizSetIds} " +
                "생성=${generated.map { it.quizSetId }} 이미돎=${skipped.map { it.quizSetId }} 실패=${result.failedQuizSetIds}"
        }
    }

    private class RegularBatchOutcome(val generatedQuizSetIds: List<Long>, val failedQuizSetIds: List<Long>)

    companion object {
        /** 한 주 배치가 통째로 실패해도 다음 주에 집을 수 있게 두 주. */
        const val RETRY_WINDOW_DAYS = 14L
        private val logger = KotlinLogging.logger {}
    }
}
