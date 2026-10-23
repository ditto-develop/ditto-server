package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.entity.MatchingType
import java.time.LocalDate
import java.time.LocalDateTime

interface QuizAnswerRepositoryCustom {
    fun deleteByMemberIdAndQuizIds(memberId: Long, quizIds: List<Long>)

    /** 답변 없는 문항은 키가 없다. quiz_answer 는 FK 없이 quiz_id 를 참조해 삭제를 DB 가 막아주지 않는다. */
    fun countAnswersPerQuiz(quizIds: List<Long>): Map<Long, Long>

    /** 그 주 활성 퀴즈셋의 선택지마다 조건에 맞는 회원의 답 수. 답이 없는 선택지도 0으로 나온다. */
    fun countAnswersPerChoiceInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<ChoiceAnswerCount>

    /** 그 주 활성 퀴즈셋을 완주한 회원마다 마지막 답을 낸 시각. quiz_progress 에 완주 시각이 없어 답 시각으로 본다. */
    fun findCompletedAtsInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<LocalDateTime>
}
