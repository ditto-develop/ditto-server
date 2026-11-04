package com.ditto.domain.system.repository

import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureFlagRepository : JpaRepository<FeatureFlag, Long> {
    fun findByKey(key: FeatureFlagKey): FeatureFlag?
}
