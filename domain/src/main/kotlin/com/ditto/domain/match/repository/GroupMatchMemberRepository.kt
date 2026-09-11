package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberRepositoryCustom
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface GroupMatchMemberRepository : JpaRepository<GroupMatchMember, Long>, GroupMatchMemberRepositoryCustom {

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): GroupMatchMember?

    /**
     * 수락 트랜잭션 전용 잠금 조회 — 한 회원이 여러 그룹을 동시에 수락하는 경쟁을 직렬화한다.
     *
     * 방 행만 잠그면 서로 다른 그룹을 동시에 수락할 때 각자 다른 행을 잠가 직렬화되지 않는다.
     * 그러면 "한 주에 채팅방 하나"가 깨지고, 자동 거절이 상대 트랜잭션의 수락을 덮어쓴다.
     * 잠금 순서는 **방 → 내 초대**로 고정한다 (ADR 0011 규칙 3).
     *
     * 퀴즈셋으로 좁히지 않고 회원 전체를 잠그는 이유: `group_match_member` 에 `quiz_set_id` 가 없어
     * 조인이 필요한데, 조인하면 남의 방 행까지 잠겨 다른 회원의 방 잠금과 교착할 수 있다.
     * `member_id` 인덱스 단일 조건이라 잠기는 행은 그 회원의 초대뿐이다(주당 최대 3건).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockByMemberId(memberId: Long): List<GroupMatchMember>

    fun findByRoomId(roomId: Long): List<GroupMatchMember>

    fun findByRoomIdIn(roomIds: List<Long>): List<GroupMatchMember>

    fun deleteByRoomIdIn(roomIds: List<Long>)

    /** 특정 퀴즈셋의 그룹 방에 참여한 멤버 레코드 조회 (GroupMatch JOIN) */
    @Query(
        """
        SELECT gmm FROM GroupMatchMember gmm
        JOIN GroupMatch gm ON gmm.roomId = gm.id
        WHERE gmm.memberId = :memberId AND gm.quizSetId = :quizSetId
        """,
    )
    fun findByMemberIdAndQuizSetId(
        @Param("memberId") memberId: Long,
        @Param("quizSetId") quizSetId: Long,
    ): List<GroupMatchMember>
}
