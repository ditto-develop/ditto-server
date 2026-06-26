package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.MatchCandidate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface MatchCandidateRepository : JpaRepository<MatchCandidate, Long> {

    /** 특정 회원이 해당 퀴즈셋에서 노출받을 후보 목록 */
    fun findByOwnerMemberIdAndQuizSetId(ownerMemberId: Long, quizSetId: Long): List<MatchCandidate>

    /** 해당 퀴즈셋의 후보를 단일 벌크 DELETE 로 모두 삭제 (재계산 전 초기화) */
    @Modifying
    @Transactional
    @Query("delete from MatchCandidate mc where mc.quizSetId = :quizSetId")
    fun deleteByQuizSetId(@Param("quizSetId") quizSetId: Long): Int

    /** owner/other 어느 쪽이든 해당 회원이 포함된 후보를 단일 벌크 DELETE 로 삭제 (더미 정리용) */
    @Modifying
    @Transactional
    @Query("delete from MatchCandidate mc where mc.ownerMemberId in :memberIds or mc.otherMemberId in :memberIds")
    fun deleteByOwnerOrOtherMemberIdIn(@Param("memberIds") memberIds: List<Long>): Int
}
