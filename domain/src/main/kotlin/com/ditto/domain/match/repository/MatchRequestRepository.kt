package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.MatchRequest
import com.ditto.domain.match.entity.MatchRequestStatus
import com.ditto.domain.match.repository.querydsl.MatchRequestRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRequestRepository : JpaRepository<MatchRequest, Long>, MatchRequestRepositoryCustom {

    fun findByFromMemberIdAndQuizSetId(fromMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun findByToMemberIdAndQuizSetId(toMemberId: Long, quizSetId: Long): List<MatchRequest>

    fun existsByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): Boolean

    fun findByFromMemberIdAndToMemberIdAndQuizSetIdAndStatus(
        fromMemberId: Long,
        toMemberId: Long,
        quizSetId: Long,
        status: MatchRequestStatus,
    ): MatchRequest?
}
