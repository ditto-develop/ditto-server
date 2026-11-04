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
     * 전환용 잠금 조회. 겹친 켜기 두 번이 둘 다 "꺼져 있었다"로 보고 켜는 순간의 처리를 두 번 하지 않게 한다.
     * MANDATORY 인 이유는 다른 잠금 조회와 같다 — 트랜잭션 없이 부르면 잠금이 곧바로 풀린다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockByKey(key: FeatureFlagKey): FeatureFlag?
}
