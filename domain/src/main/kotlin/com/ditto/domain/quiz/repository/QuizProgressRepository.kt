package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.querydsl.QuizProgressRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface QuizProgressRepository : JpaRepository<QuizProgress, Long>, QuizProgressRepositoryCustom {
    fun findByMemberIdAndQuizSetId(
        memberId: Long,
        quizSetId: Long,
    ): QuizProgress?

    fun findByMemberIdAndQuizSetIdIn(
        memberId: Long,
        quizSetIds: List<Long>,
    ): List<QuizProgress>

    fun countByQuizSetIdInAndStatus(
        quizSetIds: List<Long>,
        status: QuizProgressStatus,
    ): Long

}
