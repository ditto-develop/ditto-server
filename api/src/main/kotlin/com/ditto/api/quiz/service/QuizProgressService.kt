package com.ditto.api.quiz.service

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class QuizProgressService(
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizSetRepository: QuizSetRepository,
    private val socialAccountRepository: SocialAccountRepository,
) {
    @Transactional
    fun submitAnswer(
        principal: MemberPrincipal,
        request: SubmitAnswerRequest,
        now: LocalDateTime,
    ) {
        val memberId = resolveMemberId(principal)
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

    private fun resolveMemberId(principal: MemberPrincipal): Long {
        val socialAccount =
            socialAccountRepository
                .findByProviderAndProviderUserId(principal.provider, principal.providerUserId)
                ?: throw ErrorException(ErrorCode.UNAUTHORIZED_ERROR)
        return socialAccount.memberId
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
}
