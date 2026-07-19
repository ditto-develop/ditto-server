package com.ditto.domain.memberreport.repository

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface MemberReportRepository : JpaRepository<MemberReport, Long> {

    fun existsByReporterIdAndReportedMemberIdAndStatus(
        reporterId: Long,
        reportedMemberId: Long,
        status: MemberReportStatus,
    ): Boolean

    /** 어드민 검토 목록 — 접수 오래된 순 (SLA 대기열) */
    fun findAllByStatusOrderByCreatedAtAsc(status: MemberReportStatus): List<MemberReport>

    /** 신고자 이력 통계용 — 총 신고 수 */
    fun countByReporterId(reporterId: Long): Long

    /** 신고자 이력 통계용 — 특정 종결 상태(악의 기각 등) 수 */
    fun countByReporterIdAndStatus(reporterId: Long, status: MemberReportStatus): Long

    /**
     * 검토 종결 — RECEIVED일 때만 성공하는 조건부 UPDATE로 이중 검토를 방어한다 (반환 0이면 이미 검토됨).
     * 상태 전이 규칙(RECEIVED에서만, 종결 불변)은 이 WHERE 절이 강제한다.
     */
    fun completeReview(
        id: Long,
        result: MemberReportStatus,
        reviewedBy: Long,
        reviewerName: String,
        reviewedAt: LocalDateTime,
        reviewNote: String?,
    ): Int {
        if (result == MemberReportStatus.RECEIVED) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        return transitionReview(id, MemberReportStatus.RECEIVED, result, reviewedBy, reviewerName, reviewedAt, reviewNote)
    }

    @Transactional
    // clearAutomatically: 같은 트랜잭션에서 이미 로드된 엔티티가 벌크 UPDATE 이후 스테일 값을 돌려주는 것을 방지
    @Modifying(clearAutomatically = true)
    @Query(
        """
        update MemberReport r
        set r.status = :result, r.reviewedBy = :reviewedBy, r.reviewerName = :reviewerName,
            r.reviewedAt = :reviewedAt, r.reviewNote = :reviewNote, r.updatedAt = :reviewedAt
        where r.id = :id and r.status = :expected
        """,
    )
    fun transitionReview(
        @Param("id") id: Long,
        @Param("expected") expected: MemberReportStatus,
        @Param("result") result: MemberReportStatus,
        @Param("reviewedBy") reviewedBy: Long,
        @Param("reviewerName") reviewerName: String,
        @Param("reviewedAt") reviewedAt: LocalDateTime,
        @Param("reviewNote") reviewNote: String?,
    ): Int
}
