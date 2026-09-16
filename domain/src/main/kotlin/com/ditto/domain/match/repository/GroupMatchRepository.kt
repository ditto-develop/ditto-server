package com.ditto.domain.match.repository

import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.repository.querydsl.GroupMatchRepositoryCustom
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface GroupMatchRepository : JpaRepository<GroupMatch, Long>, GroupMatchRepositoryCustom {

    /** 해당 퀴즈셋의 그룹 전체 — 후보 재생성 시 기존 그룹을 판별하는 데 쓴다. */
    fun findByQuizSetId(quizSetId: Long): List<GroupMatch>

    /**
     * 수락 트랜잭션 전용 잠금 조회 — 동시 수락의 성사 판정을 행 잠금으로 직렬화한다.
     * PK 단건 조회로만 잠근다 (비인덱스 조건 잠금 금지 — ADR 0011).
     *
     * 수락 트랜잭션의 **첫 접근**이어야 한다(ADR 0011 규칙 5). 앞서 비잠금 조회를 하면
     * 영속성 컨텍스트에 낡은 인스턴스가 남아 잠금을 걸어도 수락자 수 판정이 어긋난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): GroupMatch?
}
