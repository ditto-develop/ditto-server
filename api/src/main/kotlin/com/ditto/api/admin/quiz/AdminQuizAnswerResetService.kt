package com.ditto.api.admin.quiz

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.admin.quiz.dto.AnswerResetPreview
import com.ditto.api.admin.quiz.dto.AnswerResetSummary
import com.ditto.api.admin.quiz.dto.MemberAnswerResetAvailability
import com.ditto.api.admin.quiz.dto.MemberAnswerResetRefusal
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** QA 를 "퀴즈 풀기"부터 다시 할 수 있게 퀴즈셋의 답·진행을 지운다. 퀴즈셋과 문항은 남긴다. */
@Service
@Transactional
class AdminQuizAnswerResetService(
    private val qaToolGuard: QaToolGuard,
    private val adminQuizQaService: AdminQuizQaService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizSetDeleter: QuizSetDeleter,
    private val memberRepository: MemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {
    @Transactional(readOnly = true)
    fun previewAllAnswersReset(quizSetId: Long): AnswerResetPreview {
        val quizSet = findQuizSet(quizSetId)
        val participantIds = quizProgressRepository.findByQuizSetIdOrderByIdAsc(quizSetId).map { it.memberId }
        val realParticipantCount = memberRepository.findAllById(participantIds)
            .count { !DummyMarker.isDummy(it.nickname) }
        return AnswerResetPreview(
            participantCount = participantIds.size,
            realParticipantCount = realParticipantCount,
            isCurrentWeek = qaToolGuard.isCurrentWeek(quizSet),
            isQuizPeriodOver = isQuizPeriodOver(quizSet),
        )
    }

    @Transactional(readOnly = true)
    fun findMemberAnswerResetAvailability(quizSetId: Long): MemberAnswerResetAvailability {
        val quizSet = findQuizSet(quizSetId)
        return MemberAnswerResetAvailability(
            refusal = findMemberAnswerResetRefusal(quizSet),
            isQuizPeriodOver = isQuizPeriodOver(quizSet),
        )
    }

    /** 답만 지우면 남은 후보 점수의 근거가 사라지므로 매칭 기록부터 지운다. */
    fun resetAllAnswers(quizSetId: Long): AnswerResetSummary {
        qaToolGuard.validateEnabled()
        qaToolGuard.validateCurrentWeek(findQuizSet(quizSetId))

        val matchingEraseSummary = adminQuizQaService.resetMatching(quizSetId)
        val answerCount = deleteAnswersIn(findQuizIdsIn(quizSetId))
        val participantCount = quizProgressRepository.deleteByQuizSetId(quizSetId)
        return AnswerResetSummary(
            matchingErase = matchingEraseSummary,
            participantCount = participantCount,
            answerCount = answerCount,
        )
    }

    /** 결과 0건으로 매칭이 돈 셋도 매칭 기록이 없어 받으므로, 그 회원의 노매칭 알림까지 지운다. */
    fun resetMemberAnswers(quizSetId: Long, memberId: Long) {
        qaToolGuard.validateEnabled()
        findMemberAnswerResetRefusal(findQuizSet(quizSetId))
            ?.let { throw WarnException(ErrorCode.BAD_REQUEST, it.message) }
        validateParticipated(memberId, quizSetId)

        quizAnswerRepository.deleteByMemberIdAndQuizIds(memberId, findQuizIdsIn(quizSetId))
        quizProgressRepository.deleteByMemberIdAndQuizSetIds(memberId, listOf(quizSetId))
        quizSetDeleter.deleteMemberMatchResultNotifications(quizSetId, memberId)
    }

    // 후보·그룹에서 한 명만 빼낼 수 없어 매칭 전 셋에서만 받는다.
    private fun findMemberAnswerResetRefusal(quizSet: QuizSet): MemberAnswerResetRefusal? = when {
        !qaToolGuard.isCurrentWeek(quizSet) -> MemberAnswerResetRefusal.NOT_CURRENT_WEEK
        quizSetDeleter.hasMatchRecords(quizSet.id) -> MemberAnswerResetRefusal.AFTER_MATCHING
        else -> null
    }

    private fun validateParticipated(memberId: Long, quizSetId: Long) {
        if (quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId) == null) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이 퀴즈셋에 참여하지 않은 회원입니다. 이미 초기화했을 수 있습니다.")
        }
    }

    // 빈 목록을 in 절에 넘기면 DB 에 따라 오류가 난다.
    private fun deleteAnswersIn(quizIds: List<Long>): Int =
        if (quizIds.isEmpty()) 0 else quizAnswerRepository.deleteByQuizIdIn(quizIds)

    private fun isQuizPeriodOver(quizSet: QuizSet): Boolean = serverTimeProvider.now() > quizSet.endDate

    private fun findQuizIdsIn(quizSetId: Long): List<Long> =
        quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }

    private fun findQuizSet(quizSetId: Long): QuizSet =
        quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
}
