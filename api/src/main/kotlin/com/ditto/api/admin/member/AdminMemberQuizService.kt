package com.ditto.api.admin.member

import com.ditto.api.admin.member.dto.MemberQuizSetRow
import com.ditto.api.admin.member.dto.MemberQuizzesView
import com.ditto.api.admin.member.dto.MemberSummary
import com.ditto.api.admin.quiz.MemberMatchingReader
import com.ditto.api.admin.quiz.QuizSetParticipation
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 회원 한 명의 퀴즈셋별 참여·진행·매칭을 모아 본다. 참여하지 않은 셋도 행으로 남겨 빠진 주가 드러나게 한다. */
@Service
@Transactional(readOnly = true)
class AdminMemberQuizService(
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberMatchingReader: MemberMatchingReader,
) {
    fun getMemberQuizzes(memberId: Long): MemberQuizzesView {
        val member = memberRepository.findById(memberId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        val quizSets = quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc()
        val progressByQuizSetId = quizProgressRepository.findByMemberId(memberId).associateBy { it.quizSetId }

        val participations = quizSets.mapNotNull { quizSet ->
            progressByQuizSetId[quizSet.id]?.let { QuizSetParticipation(quizSet, it) }
        }
        val matchingByQuizSetId = memberMatchingReader.readByQuizSetId(member, participations)
        val rows = quizSets.map { quizSet ->
            MemberQuizSetRow(quizSet, progressByQuizSetId[quizSet.id], matchingByQuizSetId[quizSet.id])
        }
        return MemberQuizzesView(MemberSummary.of(member), rows)
    }
}
