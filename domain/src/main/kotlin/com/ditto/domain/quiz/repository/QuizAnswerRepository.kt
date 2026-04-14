package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizAnswer
import com.ditto.domain.quiz.repository.querydsl.QuizAnswerRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface QuizAnswerRepository : JpaRepository<QuizAnswer, Long>, QuizAnswerRepositoryCustom {
    fun findByMemberIdAndQuizId(memberId: Long, quizId: Long): QuizAnswer?
    fun findByMemberIdAndQuizIdIn(memberId: Long, quizIds: List<Long>): List<QuizAnswer>
}
