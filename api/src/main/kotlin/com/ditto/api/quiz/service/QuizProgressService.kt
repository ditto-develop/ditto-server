package com.ditto.api.quiz.service

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.QuizProgressResponse
import com.ditto.api.quiz.dto.QuizSetWithProgressResponse
import com.ditto.api.quiz.dto.QuizWithAnswerResponse
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
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
) {
    @Transactional
    fun submitAnswer(
        principal: MemberPrincipal,
        request: SubmitAnswerRequest,
        now: LocalDateTime,
    ) {
        val memberId = principal.memberId
        val quiz = findQuiz(request.quizId)

        validateActiveQuizSet(quiz.quizSetId, now)
        validateChoice(request.quizId, request.choiceId)

        val existingQuizAnswer = quizAnswerRepository.findByMemberIdAndQuizId(memberId, request.quizId)

        if (existingQuizAnswer != null) {
            existingQuizAnswer.changeChoice(request.choiceId)
            quizAnswerRepository.save(existingQuizAnswer)
            return
        }

        quizAnswerRepository.save(QuizAnswer.create(memberId, request.quizId, request.choiceId))
        updateProgress(memberId, quiz.quizSetId)
    }

    @Transactional(readOnly = true)
    fun getProgress(
        principal: MemberPrincipal,
        now: LocalDateTime,
    ): QuizProgressResponse {
        val memberId = principal.memberId
        val quizSets = quizSetRepository.findCurrentWeekActive(now)

        if (quizSets.isEmpty()) {
            throw ErrorException(ErrorCode.NOT_FOUND)
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
        val progresses =
            quizSets
                .map { it.id }
                .let { quizSetIds -> quizProgressRepository.findByMemberIdAndQuizSetIdIn(memberId, quizSetIds) }

        if (progresses.size > 1) {
            log.error { "한 주에 여러 퀴즈셋에 참여한 사용자 발견: memberId=$memberId, quizSetIds=quizSetIds" }
            throw ErrorException(ErrorCode.INTERNAL_ERROR)
        }

        return progresses.singleOrNull()
    }

    @Transactional(readOnly = true)
    fun getQuizSetWithProgress(
        principal: MemberPrincipal,
        quizSetId: Long,
        now: LocalDateTime,
    ): QuizSetWithProgressResponse {
        val memberId = principal.memberId

        validateActiveQuizSet(quizSetId, now)

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
    fun resetProgress(principal: MemberPrincipal, now: LocalDateTime) {
        val memberId = principal.memberId
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

    private fun findQuiz(quizId: Long): Quiz =
        quizRepository
            .findById(quizId)
            .orElseThrow { ErrorException(ErrorCode.NOT_FOUND) }

    private fun validateActiveQuizSet(
        quizSetId: Long,
        now: LocalDateTime,
    ) {
        val quizSet =
            quizSetRepository
                .findById(quizSetId)
                .orElseThrow { ErrorException(ErrorCode.NOT_FOUND) }

        val isInPeriod = now >= quizSet.startDate && now <= quizSet.endDate

        if (!quizSet.isActive || !isInPeriod) {
            throw ErrorException(ErrorCode.QUIZ_NOT_IN_ACTIVE_SET)
        }
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
    ) {
        val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
        if (progress != null) {
            progress.recordAnswer()
            return
        }

        val totalCount = quizRepository.countByQuizSetId(quizSetId).toInt()
        val newProgress = QuizProgress.create(memberId, quizSetId, totalCount)

        newProgress.recordAnswer()
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
