package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.MatchRequest
import com.ditto.domain.match.entity.MatchRequestStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRequestRepository : JpaRepository<MatchRequest, Long> {

    fun findByFromMemberIdAndQuizSetId(fromMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun findByToMemberIdAndQuizSetId(toMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun existsByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): Boolean

    fun existsByQuizSetIdAndStatusAndFromMemberIdOrToMemberId(
        quizSetId: Long,
        status: MatchRequestStatus,
        fromMemberId: Long,
        toMemberId: Long,
    ): Boolean

    fun findByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): MatchRequest?
}
