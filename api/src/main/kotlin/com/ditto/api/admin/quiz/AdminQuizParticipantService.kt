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

/** 퀴즈셋 하나의 참여자(진행 기록이 있는 회원)를 진행·프로필·답변·매칭 후보와 함께 보여 준다. 더미와 실회원을 모두 담는다. */
@Service
@Transactional(readOnly = true)
class AdminQuizParticipantService(
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    private val adminParticipantMatchingReader: AdminParticipantMatchingReader,
) {
    fun getParticipants(quizSetId: Long): QuizParticipantsView {
        val quizSet = quizSetRepository.findById(quizSetId).orElseThrow { WarnException(ErrorCode.NOT_FOUND, "없는 퀴즈셋입니다: #$quizSetId") }
        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
        val progresses = quizProgressRepository.findByQuizSetIdOrderByIdAsc(quizSetId)
        val memberIds = progresses.map { it.memberId }

        val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }
        val answerContentsByMemberId = findAnswerContentsByMemberId(memberIds, quizzes)
        val participants = progresses
            .map { progress ->
                QuizParticipant.of(
                    progress = progress,
                    member = membersById[progress.memberId],
                    answerContents = answerContentsByMemberId.getValue(progress.memberId),
                )
            }
            .sortedBy { it.kind }
        val matching = adminParticipantMatchingReader.read(MatchingReadSource(quizSet, progresses, membersById))
        return QuizParticipantsView(quizSet, quizzes, participants, matching)
    }

    private fun findAnswerContentsByMemberId(memberIds: List<Long>, quizzes: List<Quiz>): Map<Long, List<String?>> {
        if (memberIds.isEmpty() || quizzes.isEmpty()) return memberIds.associateWith { emptyList() }

        val quizIds = quizzes.map { it.id }
        val choiceContentById = quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(quizIds)
            .associate { it.id to it.content }
        val answersByMemberId = quizAnswerRepository.findByMemberIdInAndQuizIdIn(memberIds, quizIds)
            .groupBy { it.memberId }
        return memberIds.associateWith { memberId ->
            val choiceIdByQuizId = answersByMemberId[memberId].orEmpty().associate { it.quizId to it.choiceId }
            quizzes.map { quiz -> choiceIdByQuizId[quiz.id]?.let { choiceContentOrDeletedLabel(it, choiceContentById) } }
        }
    }

    // 답했는데 선택지가 지워졌으면 안 푼 문항(빈칸)과 구분되게 남긴다.
    private fun choiceContentOrDeletedLabel(choiceId: Long, choiceContentById: Map<Long, String>): String =
        choiceContentById[choiceId] ?: "(삭제된 선택지 #$choiceId)"
}
