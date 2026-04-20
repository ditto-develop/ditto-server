package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.MatchRequest
import com.ditto.domain.match.entity.MatchRequestStatus
import com.ditto.domain.match.repository.querydsl.MatchRequestRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRequestRepository : JpaRepository<MatchRequest, Long>, MatchRequestRepositoryCustom {

    /** 내가 보낸 요청 목록 (requester 기준) */
    fun findByRequesterIdAndQuizSetId(requesterId: Long, quizSetId: Long): List<MatchRequest>

    /** 내가 받은 요청 목록 (receiver 기준 — memberId1/2 중 requester가 아닌 쪽) */
    fun findByMemberId1AndQuizSetIdAndRequesterIdNot(
        memberId1: Long,
        quizSetId: Long,
        requesterId: Long,
    ): List<MatchRequest>

    fun findByMemberId2AndQuizSetIdAndRequesterIdNot(
        memberId2: Long,
        quizSetId: Long,
        requesterId: Long,
    ): List<MatchRequest>

    /** 두 멤버 간 특정 quizSetId의 요청 존재 여부 (방향 무관) */
    fun existsByMemberId1AndMemberId2AndQuizSetId(
        memberId1: Long,
        memberId2: Long,
        quizSetId: Long,
    ): Boolean

    /** 두 멤버 간 특정 quizSetId + status의 요청 조회 (방향 무관) */
    fun findByMemberId1AndMemberId2AndQuizSetIdAndStatus(
        memberId1: Long,
        memberId2: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): MatchRequest?
}
