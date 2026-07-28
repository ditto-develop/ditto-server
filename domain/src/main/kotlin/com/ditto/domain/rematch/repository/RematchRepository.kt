package com.ditto.domain.rematch.repository

import com.ditto.domain.rematch.entity.Rematch
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface RematchRepository : JpaRepository<Rematch, Long> {

    /**
     * 제출 트랜잭션 전용 잠금 조회 — 동시 제출의 상호 선택 판정을 행 잠금으로 직렬화한다.
     * PK 단건 조회로만 잠근다 (비인덱스 조건 잠금 금지 — ADR 0011).
     *
     * 호출자 트랜잭션이 없으면 잠금이 즉시 풀리고 엔티티가 준영속이 되어 이후 변경이 조용히 사라진다.
     * MANDATORY로 두어 그 경우를 유실 대신 예외로 드러낸다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): Rematch?
}
