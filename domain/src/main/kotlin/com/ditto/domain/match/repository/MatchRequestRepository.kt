package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.MatchRequest
import com.ditto.domain.match.entity.MatchRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MatchRequestRepository : JpaRepository<MatchRequest, Long> {

    fun findByFromMemberIdAndQuizSetId(fromMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun findByToMemberIdAndQuizSetId(toMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun existsByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): Boolean

    // OR 우선순위 버그 방지: 파생 쿼리 대신 JPQL 명시
    @Query(
        """
        SELECT CASE WHEN COUNT(mr) > 0 THEN TRUE ELSE FALSE END
        FROM MatchRequest mr
        WHERE mr.quizSetId = :quizSetId
          AND mr.status = :status
          AND (mr.fromMemberId = :memberId OR mr.toMemberId = :memberId)
        """,
    )
    fun existsAcceptedMatchByQuizSetIdAndMemberId(
        quizSetId: Long,
        status: MatchRequestStatus,
        memberId: Long,
    ): Boolean

    fun findByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): MatchRequest?
}
