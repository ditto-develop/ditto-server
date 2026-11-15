package com.ditto.api.quiz.service

import com.ditto.api.config.quiz.QuizProperties
import com.ditto.api.match.GroupResponseDeadline
import com.ditto.api.quiz.dto.QuizProgressResponse
import com.ditto.api.quiz.dto.QuizSetWithProgressResponse
import com.ditto.api.quiz.dto.QuizWithAnswerResponse
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

@Service
class QuizProgressService(
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProperties: QuizProperties,
    private val sanctionRepository: SanctionRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val featureFlags: FeatureFlags,
) {
    @Transactional
    fun submitAnswer(
        memberId: Long,
        request: SubmitAnswerRequest,
        now: LocalDateTime,
    ) {
        val submission = AnswerSubmission(memberId, now, featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING))
        val (quiz, quizSet) = requireSubmittableQuiz(submission, request.quizId)

        validateChoice(request.quizId, request.choiceId)
        validateNotCompleted(memberId, quiz.quizSetId)
        val otherTypeProgresses = findOtherTypeProgressesOfWeek(memberId, quizSet)
        if (otherTypeProgresses.any { it.status == QuizProgressStatus.COMPLETED }) {
            validateGroupFinisherCanTakeOneToOne(submission, quizSet)
        }
        discardUnfinishedProgresses(memberId, otherTypeProgresses)

        val existingQuizAnswer = quizAnswerRepository.findByMemberIdAndQuizId(memberId, request.quizId)

        if (existingQuizAnswer != null) {
            existingQuizAnswer.changeChoice(request.choiceId)
            quizAnswerRepository.save(existingQuizAnswer)
            return
        }

        quizAnswerRepository.save(QuizAnswer.create(memberId, request.quizId, request.choiceId))
        updateProgress(memberId, quiz.quizSetId, now)
    }

    @Transactional(readOnly = true)
    fun getProgress(
        memberId: Long,
        now: LocalDateTime,
    ): QuizProgressResponse {
        val quizSets = quizSetRepository.findCurrentWeekActive(now)

        // 응답 기간(월~수) 밖이거나 이번 주 퀴즈셋이 아직 없는 상태는 정상 운영이다 — 서버 오류로 다루지 않는다.
        // 이웃 API(quiz-sets/current-week)가 빈 목록을 주는 것과 같은 결이다.
        if (quizSets.isEmpty()) {
            return QuizProgressResponse.notStarted(participantCount = 0)
        }

        val progress = findActiveProgress(memberId, quizSets)

        val participantCount =
            quizProgressRepository.countByQuizSetIdInAndStatus(
                quizSetIds = quizSets.map { it.id },
                status = QuizProgressStatus.COMPLETED,
            )

        if (progress == null) {
            return QuizProgressResponse.notStarted(participantCount)
        }

        val selectedQuizSet =
            quizSets
                .find { it.id == progress.quizSetId }
                ?: throw ErrorException(ErrorCode.INTERNAL_ERROR)

        return QuizProgressResponse(
            status = progress.status,
            quizSetId = progress.quizSetId,
            quizSetTitle = selectedQuizSet.title,
            totalQuizzes = progress.totalCount,
            answeredQuizzes = progress.answeredCount,
            participantCount = participantCount,
        )
    }

    private fun findActiveProgress(
        memberId: Long,
        quizSets: List<QuizSet>,
    ): QuizProgress? {
        val quizSetIds = quizSets.map { it.id }
        val progresses = quizProgressRepository.findByMemberIdAndQuizSetIdIn(memberId, quizSetIds)

        if (progresses.size > 1) {
            log.error { "한 주에 여러 퀴즈셋에 참여한 사용자 발견: memberId=$memberId, quizSetIds=$quizSetIds" }
            throw ErrorException(ErrorCode.INTERNAL_ERROR)
        }

        return progresses.singleOrNull()
    }

    @Transactional(readOnly = true)
    fun getQuizSetWithProgress(
        memberId: Long,
        quizSetId: Long,
        now: LocalDateTime,
    ): QuizSetWithProgressResponse {
        validateViewableQuizSet(findQuizSet(quizSetId), now)

        val quizzes = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(listOf(quizSetId))
        val quizIds = quizzes.map { it.id }

        val choicesByQuizId = findChoicesByQuizId(quizIds)
        val answersByQuizId = findAnswersByQuizId(memberId, quizIds)

        return QuizSetWithProgressResponse(
            quizzes =
                quizzes.map { quiz ->
                    QuizWithAnswerResponse.from(
                        quiz = quiz,
                        choices = choicesByQuizId[quiz.id] ?: emptyList(),
                        userAnswer = answersByQuizId[quiz.id]?.choiceId,
                    )
                },
            totalCount = quizzes.size,
        )
    }

    @Transactional
    fun resetProgress(
        memberId: Long,
        now: LocalDateTime,
    ) {
        validateNotQuizBlocked(memberId, now)

        val quizSets = quizSetRepository.findCurrentWeekActive(now)

        if (quizSets.isEmpty()) {
            log.warn { "초기화 요청 시 활성 퀴즈셋 없음: memberId=$memberId" }
            return
        }

        val quizSetIds = quizSets.map { it.id }
        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(quizSetIds).map { it.id }

        quizAnswerRepository.deleteByMemberIdAndQuizIds(memberId, quizIds)
        quizProgressRepository.deleteByMemberIdAndQuizSetIds(memberId, quizSetIds)
    }

    // 플래그가 꺼져 있으면 기존 검사 순서(요일 → 제재 → 문항 → 기간)를 그대로 지킨다.
    private fun requireSubmittableQuiz(submission: AnswerSubmission, quizId: Long): Pair<Quiz, QuizSet> {
        val (memberId, now) = submission
        if (!submission.secretMatchingEnabled) {
            validateAvailableDay(now)
            validateNotQuizBlocked(memberId, now)
            val quiz = findQuiz(quizId)
            val quizSet = findQuizSet(quiz.quizSetId)
            validateActiveQuizSet(quizSet, now)
            return quiz to quizSet
        }

        val quiz = findQuiz(quizId)
        val quizSet = findQuizSet(quiz.quizSetId)
        validateSubmissionWindow(quizSet, now)
        validateNotQuizBlocked(memberId, now)
        return quiz to quizSet
    }

    private fun validateSubmissionWindow(quizSet: QuizSet, now: LocalDateTime) {
        when (QuizSubmissionWindow.of(quizSet, now, lateSubmissionEnabled = true)) {
            QuizSubmissionWindow.LATE -> return
            QuizSubmissionWindow.LATE_CLOSED -> throw WarnException(ErrorCode.QUIZ_SECRET_MATCHING_CLOSED)
            // 기간 밖이면 기존과 같은 오류 코드가 나가도록 기존 검사를 그대로 탄다.
            QuizSubmissionWindow.REGULAR, QuizSubmissionWindow.OUTSIDE -> {
                validateAvailableDay(now)
                validateActiveQuizSet(quizSet, now)
            }
        }
    }

    private fun validateViewableQuizSet(quizSet: QuizSet, now: LocalDateTime) {
        val lateSubmissionEnabled = featureFlags.isEnabled(FeatureFlagKey.SECRET_MATCHING)
        when (QuizSubmissionWindow.of(quizSet, now, lateSubmissionEnabled)) {
            QuizSubmissionWindow.LATE -> return
            QuizSubmissionWindow.LATE_CLOSED -> throw WarnException(ErrorCode.QUIZ_SECRET_MATCHING_CLOSED)
            QuizSubmissionWindow.REGULAR, QuizSubmissionWindow.OUTSIDE -> validateActiveQuizSet(quizSet, now)
        }
    }

    private fun findQuiz(quizId: Long): Quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow { ErrorException(ErrorCode.NOT_FOUND) }

    private fun findQuizSet(quizSetId: Long): QuizSet =
        quizSetRepository
            .findById(quizSetId)
            .orElseThrow { ErrorException(ErrorCode.NOT_FOUND) }

    private fun validateActiveQuizSet(
        quizSet: QuizSet,
        now: LocalDateTime,
    ) {
        val isInPeriod = now >= quizSet.startDate && now <= quizSet.endDate

        if (!quizSet.isActive || !isInPeriod) {
            throw ErrorException(ErrorCode.QUIZ_NOT_IN_ACTIVE_SET)
        }
    }

    /** 1차 제재(경고)의 차단 구간에는 퀴즈 참여를 막는다 — 구간은 sanction이 datetime으로 보유 (ADR 0009). */
    private fun validateNotQuizBlocked(memberId: Long, now: LocalDateTime) {
        if (sanctionRepository.existsActiveWarningAt(memberId, now)) {
            throw WarnException(ErrorCode.QUIZ_BLOCKED_BY_SANCTION)
        }
    }

    private fun validateAvailableDay(now: LocalDateTime) {
        val availableDays = quizProperties.availableDays
        if (availableDays.isNotEmpty() && now.dayOfWeek !in availableDays) {
            throw ErrorException(ErrorCode.QUIZ_NOT_AVAILABLE_DAY)
        }
    }

    private fun validateNotCompleted(
        memberId: Long,
        quizSetId: Long,
    ) {
        val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (progress?.status == QuizProgressStatus.COMPLETED) {
            throw ErrorException(ErrorCode.QUIZ_ALREADY_COMPLETED)
        }
    }

    // 주차·유형당 활성 셋이 하나라, 같은 주의 다른 활성 셋은 곧 다른 유형이다.
    // 늦은 응시 때도 찾아야 해서 응답 기간이 아니라 제출한 셋의 주차로 찾는다.
    private fun findOtherTypeProgressesOfWeek(
        memberId: Long,
        quizSet: QuizSet,
    ): List<QuizProgress> {
        val otherQuizSetIds = quizSetRepository.findByWeekStartedOn(quizSet.weekStartedOn)
            .filter { it.isActive }
            .map { it.id } - quizSet.id
        if (otherQuizSetIds.isEmpty()) return emptyList()

        return quizProgressRepository.findByMemberIdAndQuizSetIdIn(memberId, otherQuizSetIds)
    }

    // 그룹을 완주했어도 성사된 그룹에 수락하지 않았으면 그룹 응답 마감 뒤에는 1:1 을 풀 수 있다.
    private fun validateGroupFinisherCanTakeOneToOne(submission: AnswerSubmission, quizSet: QuizSet) {
        val (memberId, now) = submission
        val canTakeOneToOne = submission.secretMatchingEnabled &&
            quizSet.matchingType == MatchingType.ONE_TO_ONE &&
            GroupResponseDeadline.hasPassed(quizSet.operationWeek, now) &&
            !groupMatchMemberRepository.existsAcceptedInFormedGroupOfWeek(memberId, quizSet.weekStartedOn)
        if (!canTakeOneToOne) {
            throw WarnException(ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED)
        }
        log.info { "그룹 완주자 1:1 응시 허용: memberId=$memberId quizSetId=${quizSet.id} week=${quizSet.weekStartedOn}" }
    }

    // 한 주에는 한 유형만 푼다. 다른 유형을 풀다 말았으면 그 답을 지우고 이 유형으로 갈아탄다.
    // 완주한 진행은 지우지 않는다.
    private fun discardUnfinishedProgresses(
        memberId: Long,
        otherTypeProgresses: List<QuizProgress>,
    ) {
        val unfinishedProgresses = otherTypeProgresses.filter { it.status != QuizProgressStatus.COMPLETED }
        if (unfinishedProgresses.isEmpty()) return

        val quizSetIds = unfinishedProgresses.map { it.quizSetId }
        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(quizSetIds).map { it.id }
        quizAnswerRepository.deleteByMemberIdAndQuizIds(memberId, quizIds)
        quizProgressRepository.deleteByMemberIdAndQuizSetIds(memberId, quizSetIds)
    }

    private fun validateChoice(
        quizId: Long,
        choiceId: Long,
    ) {
        val choices = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(listOf(quizId))
        if (choices.none { it.id == choiceId }) {
            throw ErrorException(ErrorCode.INVALID_CHOICE)
        }
    }

    private fun updateProgress(
        memberId: Long,
        quizSetId: Long,
        answeredAt: LocalDateTime,
    ) {
        val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (progress != null) {
            progress.recordAnswer(answeredAt)
            return
        }

        val totalCount = quizRepository.countByQuizSetId(quizSetId).toInt()
        val newProgress = QuizProgress.create(memberId, quizSetId, totalCount)

        newProgress.recordAnswer(answeredAt)
        quizProgressRepository.save(newProgress)
    }

    private fun findChoicesByQuizId(quizIds: List<Long>): Map<Long, List<QuizChoice>> {
        if (quizIds.isEmpty()) return emptyMap()

        return quizChoiceRepository
            .findByQuizIdInOrderByDisplayOrderAsc(quizIds)
            .groupBy { it.quizId }
    }

    private fun findAnswersByQuizId(
        memberId: Long,
        quizIds: List<Long>,
    ): Map<Long, QuizAnswer> {
        if (quizIds.isEmpty()) return emptyMap()

        return quizAnswerRepository
            .findByMemberIdAndQuizIdIn(memberId, quizIds)
            .associateBy { it.quizId }
    }
}

private data class AnswerSubmission(
    val memberId: Long,
    val now: LocalDateTime,
    val secretMatchingEnabled: Boolean,
)
