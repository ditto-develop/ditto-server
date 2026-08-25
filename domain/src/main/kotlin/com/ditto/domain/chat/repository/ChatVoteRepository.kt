package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatVote
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface ChatVoteRepository : JpaRepository<ChatVote, Long> {

    /** 방의 투표 전체 — 방 진입·재접속 복구용. 최신이 앞이다. */
    fun findAllByRoomIdOrderByIdDesc(roomId: Long): List<ChatVote>

    /** 방의 열린 투표. 방당 하나뿐이다(chat_vote_uk_1). */
    fun findByOpenRoomId(roomId: Long): ChatVote?

    /**
     * 투표 행을 잠그고 읽는다 — cast(치환)·마감처럼 "상태 확인 후 전이·집계"가 한 덩어리인 경로용.
     *
     * MANDATORY 인 이유는 [ChatRoomRepository.findWithLockById]와 같다 — 호출자 트랜잭션이 없으면
     * 잠금이 즉시 풀려 유실이 조용히 일어나는 대신 예외로 드러낸다.
     * 잠금 순서는 항상 방 → 멤버 → 투표다(그룹 이탈 트랜잭션과 역전되면 데드락).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): ChatVote?

    /**
     * 방 종료가 열린 투표를 함께 닫을 때 쓴다. open_room_id 가 유일 인덱스라 단건 잠금이다.
     * 비잠금 [findByOpenRoomId]로 먼저 찾으면 만료 sweep 의 스냅샷에 갇혀 그 사이 생긴 투표를
     * 놓친다 — ADR 0011 규칙 5·7.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockByOpenRoomId(openRoomId: Long): ChatVote?
}
