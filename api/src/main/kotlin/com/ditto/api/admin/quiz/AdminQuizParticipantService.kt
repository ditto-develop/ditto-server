package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizParticipant
import com.ditto.api.admin.quiz.dto.QuizParticipantsView
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.Quiz
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 퀴즈셋 하나의 참여자(진행 기록이 있는 회원)를 진행·프로필·답변과 함께 보여 준다. 더미와 실회원을 모두 담는다. */
@Service
@Transactional(readOnly = true)
class AdminQuizParticipantService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
) {
    fun getParticipants(quizSetId: Long): QuizParticipantsView {
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
        val progresses = quizProgressRepository.findByQuizSetIdOrderByIdAsc(quizSetId)
        val memberIds = progresses.map { it.memberId }

        val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }
        val answerContentsByMemberId = findAnswerContentsByMemberId(memberIds, quizzes)
        val participants = progresses.map { progress ->
            QuizParticipant.of(
                progress = progress,
                member = membersById[progress.memberId],
                answerContents = answerContentsByMemberId[progress.memberId] ?: quizzes.map { null },
            )
        }
        return QuizParticipantsView(quizSet, quizzes, participants)
    }

    /** 회원별로 문항 순서에 맞춰 고른 선택지 내용을 늘어놓는다. 안 푼 문항은 null 이다. */
    private fun findAnswerContentsByMemberId(memberIds: List<Long>, quizzes: List<Quiz>): Map<Long, List<String?>> {
        if (memberIds.isEmpty() || quizzes.isEmpty()) return emptyMap()

        val quizIds = quizzes.map { it.id }
        val choiceContentById = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(quizIds)
            .associate { it.id to it.content }
        return quizAnswerRepository.findByMemberIdInAndQuizIdIn(memberIds, quizIds)
            .groupBy { it.memberId }
            .mapValues { (_, answers) ->
                val choiceIdByQuizId = answers.associate { it.quizId to it.choiceId }
                quizzes.map { quiz -> choiceIdByQuizId[quiz.id]?.let(choiceContentById::get) }
            }
    }
}
