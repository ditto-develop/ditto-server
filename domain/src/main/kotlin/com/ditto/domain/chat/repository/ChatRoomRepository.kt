package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.querydsl.ChatRoomRepositoryCustom
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface ChatRoomRepository : JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {

    fun findBySourceTypeAndSourceId(sourceType: ChatRoomType, sourceId: Long): ChatRoom?

    fun existsBySourceTypeAndSourceId(sourceType: ChatRoomType, sourceId: Long): Boolean

    /**
     * 종료 판정을 위한 단건 잠금 조회. 겹친 종료 요청과 만료 스케줄러가 서로의 커밋 전 상태를 보고
     * 둘 다 "아직 안 끝났다"로 판단하는 것을 막는다 — 잠금이 순서를 만들어야 뒤 요청이 최신 상태를 본다.
     *
     * 호출자 트랜잭션이 없으면 잠금이 즉시 풀리고 엔티티가 준영속이 되어 이후 변경이 조용히 사라진다.
     * MANDATORY로 두어 그 경우를 유실 대신 예외로 드러낸다 (ADR 0011 의 재매칭 선례와 같은 이유).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): ChatRoom?

}
