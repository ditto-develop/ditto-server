package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.repository.querydsl.QuizAnswerRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface QuizAnswerRepository : JpaRepository<QuizAnswer, Long>, QuizAnswerRepositoryCustom {
    fun findByMemberIdAndQuizId(memberId: Long, quizId: Long): QuizAnswer?
    fun findByMemberIdAndQuizIdIn(memberId: Long, quizIds: List<Long>): List<QuizAnswer>

    /** 여러 회원의 여러 문항 답변을 한 번에 조회 (매칭 점수 계산용 벌크 로드) */
    fun findByMemberIdInAndQuizIdIn(memberIds: List<Long>, quizIds: List<Long>): List<QuizAnswer>
}
