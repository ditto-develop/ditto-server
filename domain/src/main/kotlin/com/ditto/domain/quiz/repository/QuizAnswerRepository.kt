package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.repository.querydsl.QuizAnswerRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface QuizAnswerRepository : JpaRepository<QuizAnswer, Long>, QuizAnswerRepositoryCustom {
    fun findByMemberIdAndQuizId(memberId: Long, quizId: Long): QuizAnswer?
    fun findByMemberIdAndQuizIdIn(memberId: Long, quizIds: List<Long>): List<QuizAnswer>

    /** 여러 회원의 여러 문항 답변을 한 번에 조회 (매칭 점수 계산용 벌크 로드) */
    fun findByMemberIdInAndQuizIdIn(memberIds: List<Long>, quizIds: List<Long>): List<QuizAnswer>

    /** 여러 회원의 답변을 단일 벌크 DELETE 로 삭제 (더미 정리용) */
    @Modifying
    @Transactional
    @Query("delete from QuizAnswer qa where qa.memberId in :memberIds")
    fun deleteByMemberIdIn(@Param("memberIds") memberIds: List<Long>): Int
}
