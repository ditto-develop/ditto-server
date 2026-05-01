package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMatchMemberRepository : JpaRepository<GroupMatchMember, Long> {

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByRoomId(roomId: Long): List<GroupMatchMember>

    /** 특정 퀴즈셋의 그룹 방에 참여 중인지 확인 (GroupMatch JOIN) */
    @Query(
        """
        SELECT CASE WHEN COUNT(gmm) > 0 THEN true ELSE false END
        FROM GroupMatchMember gmm
        JOIN GroupMatch gm ON gmm.roomId = gm.id
        WHERE gmm.memberId = :memberId AND gm.quizSetId = :quizSetId
        """,
    )
    fun existsByMemberIdAndQuizSetId(
        @Param("memberId") memberId: Long,
        @Param("quizSetId") quizSetId: Long,
    ): Boolean
}
