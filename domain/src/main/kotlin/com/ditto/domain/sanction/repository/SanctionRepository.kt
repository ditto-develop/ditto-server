package com.ditto.domain.sanction.repository

import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface SanctionRepository : JpaRepository<Sanction, Long> {

    fun findAllByMemberIdAndStatus(memberId: Long, status: SanctionStatus): List<Sanction>

    /** 특정 상태이면서 종료 일시가 지난 제재 목록 (만료 일괄 종결용). */
    fun findAllByStatusAndEndsAtLessThanEqual(status: SanctionStatus, endsAt: LocalDateTime): List<Sanction>

    /** 주어진 시각에 유효한 1차 제재(경고)가 있는지 — 퀴즈 참여 차단 판정용 */
    fun existsActiveWarningAt(memberId: Long, now: LocalDateTime): Boolean =
        existsByMemberIdAndLevelAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
            memberId = memberId,
            level = SanctionLevel.WARNING,
            status = SanctionStatus.ACTIVE,
            startsAt = now,
            endsAt = now,
        )

    fun existsByMemberIdAndLevelAndStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
        memberId: Long,
        level: SanctionLevel,
        status: SanctionStatus,
        startsAt: LocalDateTime,
        endsAt: LocalDateTime,
    ): Boolean
}
