package com.ditto.domain.system.repository

import com.ditto.domain.system.entity.ServerTimeOverride
import org.springframework.data.jpa.repository.JpaRepository

interface ServerTimeOverrideRepository : JpaRepository<ServerTimeOverride, Long> {
    /** 단일 행 운영 — 가장 먼저 생성된 행을 사용한다. */
    fun findFirstByOrderByIdAsc(): ServerTimeOverride?
}
