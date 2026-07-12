package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

/**
 * 회원 신고. 접수 후 상태 전이는 어드민 검토에서만 일어난다.
 *
 * 이 코드베이스에서 접두어 없는 `Report`는 사용하지 않는다 — 신고는 `MemberReport`(API 표면은 user-report),
 * 문서·통계 기능은 `Summary`/`Statistics` 등 다른 이름을 쓴다.
 */
@Entity
@Table(
    name = "member_report",
    indexes = [
        Index(name = "member_report_index_1", columnList = "status, created_at"),
        Index(name = "member_report_index_2", columnList = "reported_member_id"),
        Index(name = "member_report_index_3", columnList = "reporter_id, reported_member_id, status"),
    ],
)
class MemberReport private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("신고자 회원 ID")
    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Comment("피신고자 회원 ID")
    @Column(name = "reported_member_id", nullable = false)
    val reportedMemberId: Long,

    @Comment("신고 사유")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val reason: MemberReportReason,

    @Comment("신고 접수 위치")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: MemberReportSource,

    @Comment("상세 설명 (기타 사유는 필수)")
    @Column(length = DETAIL_MAX_LENGTH)
    val detail: String? = null,

    @Comment("신고 처리 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: MemberReportStatus = MemberReportStatus.RECEIVED,

    @Comment("검토자 회원 ID")
    @Column(name = "reviewed_by")
    val reviewedBy: Long? = null,

    @Comment("검토자 표시명 스냅샷 (계정 삭제 후에도 감사 기록 보존)")
    @Column(name = "reviewer_name", length = 50)
    val reviewerName: String? = null,

    @Comment("검토 일시")
    @Column(name = "reviewed_at")
    val reviewedAt: LocalDateTime? = null,

    @Comment("검토 메모")
    @Column(name = "review_note", length = 500)
    val reviewNote: String? = null,
) : BaseEntity() {

    companion object {
        const val DETAIL_MAX_LENGTH = 500

        fun receive(
            reporterId: Long,
            reportedMemberId: Long,
            reason: MemberReportReason,
            source: MemberReportSource,
            detail: String? = null,
        ): MemberReport {
            if (reporterId == reportedMemberId) {
                throw WarnException(ErrorCode.CANNOT_REPORT_SELF)
            }
            if (reason.requiresDetail && detail.isNullOrBlank()) {
                throw WarnException(ErrorCode.REPORT_ETC_REASON_REQUIRED)
            }
            return MemberReport(
                reporterId = reporterId,
                reportedMemberId = reportedMemberId,
                reason = reason,
                source = source,
                detail = detail,
            )
        }
    }
}
