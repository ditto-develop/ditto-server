package com.ditto.api.admin.quiz

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.admin.quiz.dto.AnswerResetPreview
import com.ditto.api.admin.quiz.dto.AnswerResetSummary
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
) {
    @Transactional(readOnly = true)
    fun previewAllAnswersReset(quizSetId: Long): AnswerResetPreview {
        val participantIds = quizProgressRepository.findByQuizSetIdOrderByIdAsc(quizSetId).map { it.memberId }
        val realParticipantCount = memberRepository.findAllById(participantIds)
            .count { !DummyMarker.isDummy(it.nickname) }
        return AnswerResetPreview(participantCount = participantIds.size, realParticipantCount = realParticipantCount)
    }

    /** 매칭 기록이 남으면 후보 점수가 근거로 삼던 답이 사라져 어긋나므로 매칭 기록부터 지운다. */
    fun resetAllAnswers(quizSetId: Long): AnswerResetSummary {
        val matching = adminQuizQaService.resetMatching(quizSetId)
        val quizIds = findQuizIdsOf(quizSetId)
        val answerCount = if (quizIds.isEmpty()) 0 else quizAnswerRepository.deleteByQuizIdIn(quizIds)
        val participantCount = quizProgressRepository.deleteByQuizSetId(quizSetId)
        return AnswerResetSummary(matching = matching, participantCount = participantCount, answerCount = answerCount)
    }

    /** 한 명만 빼고 후보·그룹을 고칠 수 없으므로 매칭 전 셋에서만 받는다. */
    fun resetMemberAnswers(quizSetId: Long, memberId: Long) {
        qaToolGuard.validateEnabled()
        val quizSet = findQuizSet(quizSetId)
        qaToolGuard.validateCurrentWeek(quizSet)
        validateBeforeMatching(quizSet)
        quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)
            ?: throw WarnException(ErrorCode.NOT_FOUND, "이 퀴즈셋에 참여하지 않은 회원입니다.")

        quizAnswerRepository.deleteByMemberIdAndQuizIds(memberId, findQuizIdsOf(quizSetId))
        quizProgressRepository.deleteByMemberIdAndQuizSetIds(memberId, listOf(quizSetId))
    }

    private fun validateBeforeMatching(quizSet: QuizSet) {
        if (quizSetDeleter.hasMatchRecords(quizSet.id)) {
            throw WarnException(
                ErrorCode.BAD_REQUEST,
                "매칭이 진행된 퀴즈셋은 회원별로 초기화할 수 없습니다. 퀴즈셋 상세에서 전체 답·진행 초기화를 쓰세요.",
            )
        }
    }

    private fun findQuizIdsOf(quizSetId: Long): List<Long> =
        quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }

    private fun findQuizSet(quizSetId: Long): QuizSet =
        quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
}
