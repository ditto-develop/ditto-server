package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoomMember
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface ChatRoomMemberRepository : JpaRepository<ChatRoomMember, Long> {

    /** 내가 참여한 채팅방 멤버 레코드 목록 */
    fun findByMemberId(memberId: Long): List<ChatRoomMember>

    /** 내가 참여한 채팅방 수 — 프로필 통계의 "매칭 성사"(= 채팅방 개설 횟수)용 */
    fun countByMemberId(memberId: Long): Long

    fun findByRoomIdIn(roomIds: Collection<Long>): List<ChatRoomMember>

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun existsByRoomIdAndMemberId(roomId: Long, memberId: Long): Boolean

    /**
     * 방의 멤버 행 전부를 잠그고 읽는다 — 그룹 이탈의 멱등 검사와 잔여 인원 카운트용.
     *
     * 잠금 없는 조회로 세면 안 되는 이유: InnoDB(REPEATABLE READ)의 읽기 스냅샷은 트랜잭션의
     * 첫 비잠금 SELECT 시점에 고정되므로, 방 행 잠금을 기다리는 사이 커밋된 다른 멤버의 이탈이
     * 카운트에 보이지 않는다 — 두 명이 동시에 나가면 둘 다 "잔여 2명"으로 계산해 해체가 누락된다.
     * 잠금 읽기는 스냅샷과 무관하게 최신 커밋을 읽는다.
     *
     * MANDATORY 인 이유는 [ChatRoomRepository.findWithLockById]와 같다 — 호출자 트랜잭션이 없으면
     * 잠금이 즉시 풀려 유실이 조용히 일어나는 대신 예외로 드러낸다. 잠금 순서는 항상 방 → 멤버다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findAllWithLockByRoomId(roomId: Long): List<ChatRoomMember>
}
