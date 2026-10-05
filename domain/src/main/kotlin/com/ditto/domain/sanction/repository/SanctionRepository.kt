package com.ditto.domain.sanction.repository

import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.entity.SanctionStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface SanctionRepository : JpaRepository<Sanction, Long> {

    fun findByMemberIdInOrMemberReportIdIn(
        memberIds: Collection<Long>,
        memberReportIds: Collection<Long>,
    ): List<Sanction>

    fun findAllByMemberIdAndStatus(memberId: Long, status: SanctionStatus): List<Sanction>

    /** 회원별 제재 이력 — 최신순 */
    fun findAllByMemberIdOrderByIdDesc(memberId: Long): List<Sanction>

    /**
     * 누적 제재 수. 허위 신고자 제재(FALSE_REPORT)와 어드민이 해제한 제재(LIFTED, 오처리 정정)는 세지 않는다.
     * 신고 상세의 "이번이 N차 제재"는 이 값 + 1이다.
     */
    fun countStrikes(memberId: Long): Long =
        countByMemberIdAndOriginNotAndStatusNot(memberId, SanctionOrigin.FALSE_REPORT, SanctionStatus.LIFTED)

    fun countByMemberIdAndOriginNotAndStatusNot(
        memberId: Long,
        origin: SanctionOrigin,
        status: SanctionStatus,
    ): Long

    /**
     * 직권 해제 — ACTIVE일 때만 성공하는 조건부 UPDATE로 이중 해제를 방어한다 (반환 0이면 이미 종결됨).
     * 신고 검토의 completeReview와 같은 관용구.
     */
    fun liftIfActive(id: Long, now: LocalDateTime): Int =
        transitionStatus(id, SanctionStatus.ACTIVE, SanctionStatus.LIFTED, now)

    @Transactional
    // clearAutomatically: 같은 트랜잭션에서 이미 로드된 엔티티가 벌크 UPDATE 이후 스테일 값을 돌려주는 것을 방지
    @Modifying(clearAutomatically = true)
    @Query(
        """
        update Sanction s
        set s.status = :result, s.updatedAt = :now
        where s.id = :id and s.status = :expected
        """,
    )
    fun transitionStatus(
        @Param("id") id: Long,
        @Param("expected") expected: SanctionStatus,
        @Param("result") result: SanctionStatus,
        @Param("now") now: LocalDateTime,
    ): Int

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
