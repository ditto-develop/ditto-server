package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.querydsl.QuizProgressRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

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

    /**
     * 회원이 특정 상태로 남긴 진행 수 — 프로필 통계의 "참여 주차"용.
     * 진행은 (회원, 퀴즈셋) 단위로 하나뿐이라 COMPLETED 개수가 곧 완주한 주차 수다.
     */
    fun countByMemberIdAndStatus(
        memberId: Long,
        status: QuizProgressStatus,
    ): Long

    /** 여러 회원의 진행을 단일 벌크 DELETE 로 삭제 */
    @Modifying
    @Transactional
    @Query("delete from QuizProgress qp where qp.memberId in :memberIds")
    fun deleteByMemberIdIn(@Param("memberIds") memberIds: List<Long>): Int
}
