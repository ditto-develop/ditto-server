package com.ditto.domain.sanction.entity

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
 * 제재 이력의 SSOT — 차수·기간·조치자의 진실은 이 엔티티가 갖고,
 * `Member.status`는 매 요청 집행용 반영값이다 (ADR 0009).
 */
@Entity
@Table(
    name = "sanction",
    indexes = [
        Index(name = "sanction_index_1", columnList = "member_id, status"),
        Index(name = "sanction_index_2", columnList = "status, ends_at"),
    ],
)
class Sanction private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("피제재 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("근거 신고 ID (어드민 직권 제재는 NULL)")
    @Column(name = "member_report_id")
    val memberReportId: Long? = null,

    @Comment("제재 발생 경위")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val origin: SanctionOrigin,

    @Comment("제재 수위")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val level: SanctionLevel,

    @Comment("제재 시작 일시")
    @Column(name = "starts_at", nullable = false)
    val startsAt: LocalDateTime,

    @Comment("제재 종료 일시 (영구 차단은 NULL)")
    @Column(name = "ends_at")
    val endsAt: LocalDateTime? = null,

    @Comment("제재 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SanctionStatus = SanctionStatus.ACTIVE,

    @Comment("조치자 회원 ID")
    @Column(name = "created_by", nullable = false)
    val createdBy: Long,

    @Comment("조치자 표시명 스냅샷 (계정 삭제 후에도 감사 기록 보존)")
    @Column(name = "creator_name", nullable = false, length = 50)
    val creatorName: String,

    @Comment("조치 메모")
    @Column(length = 500)
    val note: String? = null,
) : BaseEntity() {

    /** 주어진 시각 기준 아직 효력이 있는 제재인지 — 영구 차단(endsAt 없음)은 항상 유효하다. */
    fun isEffectiveAt(now: LocalDateTime): Boolean {
        if (status != SanctionStatus.ACTIVE) return false
        val endsAt = this.endsAt ?: return true
        return endsAt > now
    }

    /** 기간 만료로 종결한다 (배치·로그인 시 원복 흐름에서 호출). */
    fun expire() {
        transitionTo(SanctionStatus.EXPIRED)
    }

    /** 어드민 직권 해제로 종결한다 (오처리 정정 수단). */
    fun lift() {
        transitionTo(SanctionStatus.LIFTED)
    }

    private fun transitionTo(target: SanctionStatus) {
        if (status != SanctionStatus.ACTIVE) {
            throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = target
    }

    companion object {

        fun impose(
            memberId: Long,
            origin: SanctionOrigin,
            level: SanctionLevel,
            startsAt: LocalDateTime,
            endsAt: LocalDateTime?,
            createdBy: Long,
            creatorName: String,
            memberReportId: Long? = null,
            note: String? = null,
        ): Sanction {
            validatePeriod(level, startsAt, endsAt)
            return Sanction(
                memberId = memberId,
                memberReportId = memberReportId,
                origin = origin,
                level = level,
                startsAt = startsAt,
                endsAt = endsAt,
                createdBy = createdBy,
                creatorName = creatorName,
                note = note,
            )
        }

        /** 영구 차단은 종료일이 없어야 하고, 기간 제재(경고·정지)는 종료일이 시작일 이후여야 한다. */
        private fun validatePeriod(level: SanctionLevel, startsAt: LocalDateTime, endsAt: LocalDateTime?) {
            if (level == SanctionLevel.PERMANENT_BAN) {
                if (endsAt != null) {
                    throw WarnException(ErrorCode.BAD_REQUEST)
                }
                return
            }
            if (endsAt == null || !endsAt.isAfter(startsAt)) {
                throw WarnException(ErrorCode.BAD_REQUEST)
            }
        }
    }
}
