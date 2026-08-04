package com.ditto.domain.rematch.repository

import com.ditto.domain.rematch.entity.Rematch
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface RematchRepository : JpaRepository<Rematch, Long> {

    /**
     * 소스 그룹에 이미 만들어진 쌍. 그룹 채팅 종료 시 쌍을 멱등하게 만들려고 기존 것을 먼저 읽는다 —
     * `saveAll`은 쌍 여럿을 한 번에 넣으므로 그중 하나만 유일키를 위반해도 호출 전체가 실패해
     * 정상 쌍까지 들어가지 못한다. 쌍 생성이 평가보다 앞서므로 그 방의 평가도 열리지 않는다.
     *
     * 먼저 읽어 막는 것은 **재실행**(종료 이벤트 재전달·누락 복구 재시도)이다. 동시 실행은 이 경로에
     * 조회와 저장을 묶는 트랜잭션이 없어 막지 못하고, `rematch_uk_1`이 최종 방어선이 된다 —
     * 진 호출만 실패하고 이긴 호출이 평가까지 열므로 중복 쌍도 지연도 생기지 않는다.
     */
    fun findAllBySourceGroupMatchId(sourceGroupMatchId: Long): List<Rematch>

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

    /**
     * 소스 그룹의 정규화된 쌍으로 잠금 조회 — 리뷰 제출은 재매칭 ID를 모르고 "누구를 평가하는지"만 안다.
     * 유일키 `(source_group_match_id, member_id_1, member_id_2)` 단건 조회이므로 잠금 범위가 한 행이다 (ADR 0011).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockBySourceGroupMatchIdAndMemberId1AndMemberId2(
        sourceGroupMatchId: Long,
        memberId1: Long,
        memberId2: Long,
    ): Rematch?
}
