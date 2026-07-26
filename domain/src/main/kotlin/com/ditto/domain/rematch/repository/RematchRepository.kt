package com.ditto.domain.rematch.repository

import com.ditto.domain.rematch.entity.Rematch
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface RematchRepository : JpaRepository<Rematch, Long> {

    /**
     * 제출 트랜잭션 전용 잠금 조회 — 동시 제출의 상호 선택 판정을 행 잠금으로 직렬화한다.
     * PK 단건 조회로만 잠근다 (비인덱스 조건 잠금 금지 — ADR 0011).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): Rematch?
}
