package com.ditto.domain.system.repository

import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface FeatureFlagRepository : JpaRepository<FeatureFlag, Long> {
    fun findByKey(key: FeatureFlagKey): FeatureFlag?

    /**
     * 지금 정의된 키의 행만 읽는다. `flag_key` 는 enum 으로 읽혀서, 지운 키의 행이 DB 에 남아 있으면 `findAll()` 은
     * 그 행을 만들다 예외로 끝난다(플래그를 묻는 모든 경로가 같이 멈춘다). 조건을 SQL 에 걸어 그 행은 아예 읽지 않는다.
     */
    fun findAllByKeyIn(keys: Collection<FeatureFlagKey>): List<FeatureFlag>

    /**
     * 전환용 잠금 조회. 겹친 켜기 두 번이 둘 다 "꺼져 있었다"로 보고 켜는 순간의 처리를 두 번 하지 않게 한다.
     * MANDATORY 인 이유는 다른 잠금 조회와 같다 — 트랜잭션 없이 부르면 잠금이 곧바로 풀린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockByKey(key: FeatureFlagKey): FeatureFlag?
}
