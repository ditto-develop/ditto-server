package com.ditto.api.system.featureflag

import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 플래그 행이 없으면 만든다. 처음 켜기를 두 번 눌러 둘 다 행을 만들려 하면 유니크가 한쪽을 막는데,
 * 같은 트랜잭션에서 그 예외를 잡으면 트랜잭션이 롤백 전용이 되어 결국 500 이 난다. 그래서 자기 트랜잭션에서 만들고,
 * 진 쪽은 이 트랜잭션만 버린 채 바깥([FeatureFlagAdminService])에서 이긴 쪽 행을 읽는다.
 *
 * 반드시 다른 빈에서 불러야 한다 — 같은 빈 안에서 부르면 프록시를 거치지 않아 `REQUIRES_NEW`가 적용되지 않는다.
 */
@Component
class FeatureFlagRowCreator(
    private val featureFlagRepository: FeatureFlagRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createIfAbsent(key: FeatureFlagKey) {
        if (featureFlagRepository.findByKey(key) == null) {
            featureFlagRepository.saveAndFlush(FeatureFlag.of(key))
        }
    }
}
