package com.ditto.api.quiz.service

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class QuizProgressService(
    private val quizAnswerRepository: QuizAnswerRepository,
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

        validateQuizInActiveSet(request.quizId, now)
        validateChoice(request.quizId, request.choiceId)

        upsertAnswer(memberId, request.quizId, request.choiceId)
    }

    private fun resolveMemberId(principal: MemberPrincipal): Long {
        val socialAccount =
            socialAccountRepository
                .findByProviderAndProviderUserId(principal.provider, principal.providerUserId)
                ?: throw ErrorException(ErrorCode.UNAUTHORIZED_ERROR)
        return socialAccount.memberId
    }

    private fun validateQuizInActiveSet(
        quizId: Long,
        now: LocalDateTime,
    ) {
        val quiz =
            quizRepository
                .findById(quizId)
                .orElseThrow { ErrorException(ErrorCode.NOT_FOUND) }
        val quizSet =
            quizSetRepository
                .findById(quiz.quizSetId)
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
        val valid = choices.any { it.id == choiceId }
        if (!valid) {
            throw ErrorException(ErrorCode.INVALID_CHOICE)
        }
    }

    private fun upsertAnswer(
        memberId: Long,
        quizId: Long,
        choiceId: Long,
    ) {
        val existing = quizAnswerRepository.findByMemberIdAndQuizId(memberId, quizId)

        if (existing != null) {
            existing.changeChoice(choiceId)
            return
        }

        val newQuizAnswer = QuizAnswer.create(memberId, quizId, choiceId)
        quizAnswerRepository.save(newQuizAnswer)
    }
}
