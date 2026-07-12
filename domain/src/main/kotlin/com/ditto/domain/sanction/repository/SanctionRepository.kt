package com.ditto.domain.sanction.repository

import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.entity.SanctionStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface SanctionRepository : JpaRepository<Sanction, Long> {

    fun findAllByMemberIdAndStatus(memberId: Long, status: SanctionStatus): List<Sanction>

    /** 회원별 제재 이력 — 최신순 */
    fun findAllByMemberIdOrderByIdDesc(memberId: Long): List<Sanction>

    /**
     * 차수 산정용 유효 제재 수 — 허위 신고자 제재(FALSE_REPORT)와 직권 해제(LIFTED, 오처리 정정)는
     * 피신고 차수에 산입하지 않는다. 어드민 화면의 추천 차수 = 이 값 + 1.
     */
    fun countStrikes(memberId: Long): Long =
        countByMemberIdAndOriginNotAndStatusNot(memberId, SanctionOrigin.FALSE_REPORT, SanctionStatus.LIFTED)

    fun countByMemberIdAndOriginNotAndStatusNot(
        memberId: Long,
        origin: SanctionOrigin,
        status: SanctionStatus,
    ): Long

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
