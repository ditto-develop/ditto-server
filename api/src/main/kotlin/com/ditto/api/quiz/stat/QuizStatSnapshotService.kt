package com.ditto.api.quiz.stat

import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.quiz.entity.QuizChoiceStat
import com.ditto.domain.quiz.repository.QuizChoiceStatRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.repository.QuizSetStatRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 마감 통계 스냅샷 조회·생성. 목 00:00 배치가 만들고([snapshotEnded]), 배치가 실패했거나 서버가 그 순간 내려가 있어
 * 없으면 조회하는 쪽이 그 자리에서 만든다([getOrCreate]).
 *
 * 트랜잭션을 열지 않는다 — 쓰기는 [QuizStatSnapshotWriter]가 셋마다 자기 트랜잭션으로 한다.
 */
@Service
class QuizStatSnapshotService(
    private val quizSetRepository: QuizSetRepository,
    private val quizSetStatRepository: QuizSetStatRepository,
    private val quizChoiceStatRepository: QuizChoiceStatRepository,
    private val quizStatSnapshotWriter: QuizStatSnapshotWriter,
) {

    /**
     * 최근 [RETRY_WINDOW_DAYS]일 안에 마감됐고 스냅샷이 없는 셋을 만든다(멱등). 실패한 셋은 로그만 남기고 넘어간다 —
     * 조회 시 지연 생성이 메운다.
     *
     * @return 이번에 만든 셋 수
     */
    fun snapshotEnded(now: LocalDateTime): Int =
        quizSetRepository.findEndedQuizSetIdsWithoutStat(now.minusDays(RETRY_WINDOW_DAYS), now)
            .count { quizSetId ->
                runCatchingExceptions { createIfAbsent(quizSetId) }
                    .onFailure { logger.warn(it) { "퀴즈 통계 스냅샷 생성 실패 — 조회 시 다시 만든다: quizSetId=$quizSetId" } }
                    .isSuccess
            }
            .also { if (it > 0) logger.info { "퀴즈 통계 스냅샷 생성: ${it}건" } }

    /** 마감된 셋의 스냅샷. 없으면 만든다. 마감 전 셋에 부르면 그 순간까지의 답으로 굳어 버리므로 호출자가 마감을 확인한다. */
    fun getOrCreate(quizSetId: Long): QuizStatSnapshot {
        if (quizSetStatRepository.findByQuizSetId(quizSetId) == null) {
            createIfAbsent(quizSetId)
        }
        val setStat = quizSetStatRepository.findByQuizSetId(quizSetId)
            ?: throw IllegalStateException("퀴즈 통계 스냅샷을 만들지 못했다: quizSetId=$quizSetId")
        return QuizStatSnapshot(
            participantCount = setStat.participantCount,
            overlapThreshold = setStat.overlapThreshold,
            choices = quizChoiceStatRepository.findByQuizSetId(quizSetId),
        )
    }

    private fun createIfAbsent(quizSetId: Long) {
        try {
            quizStatSnapshotWriter.write(quizSetId)
        } catch (e: DataIntegrityViolationException) {
            // 동시에 만든 쪽이 이겼다. 그쪽 결과를 읽는다.
            logger.info { "퀴즈 통계 스냅샷이 이미 있다(동시 생성): quizSetId=$quizSetId" }
        }
    }

    companion object {
        /** 매칭 배치와 같은 창. 이보다 오래된 셋은 조회 시 지연 생성된다. */
        private const val RETRY_WINDOW_DAYS = 14L
        private val logger = KotlinLogging.logger {}
    }
}

/** 한 퀴즈셋의 마감 통계. 소수 의견 여부는 저장하지 않고 선택 수와 참여자 수로 그때 판정한다(규칙은 한곳). */
class QuizStatSnapshot(
    val participantCount: Int,
    val overlapThreshold: Int,
    choices: List<QuizChoiceStat>,
) {
    private val choicesById: Map<Long, QuizChoiceStat> = choices.associateBy { it.choiceId }

    fun percentOf(choiceId: Long): Int = choicesById[choiceId]?.percent ?: 0

    fun isMinority(choiceId: Long): Boolean =
        QuizStatCalculator.isMinority(choicesById[choiceId]?.selectCount ?: 0, participantCount)
}
