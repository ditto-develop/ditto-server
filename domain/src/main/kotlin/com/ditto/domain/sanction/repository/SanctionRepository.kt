package com.ditto.domain.sanction.repository

import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionStatus
import org.springframework.data.jpa.repository.JpaRepository

interface SanctionRepository : JpaRepository<Sanction, Long> {

    fun findAllByMemberIdAndStatus(memberId: Long, status: SanctionStatus): List<Sanction>
}
