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

    /** 해당 퀴즈셋에서 특정 상태(예: COMPLETED)인 참여자 진행 목록 */
    fun findByQuizSetIdAndStatus(
        quizSetId: Long,
        status: QuizProgressStatus,
    ): List<QuizProgress>
}
