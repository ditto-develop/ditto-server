package com.ditto.api.admin.report

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.report.dto.ReviewDecision
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.sanction.entity.Sanction
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신고 검토 처리 — 신고 종결과 제재 적용을 한 트랜잭션으로 묶는다 (ADR 0009: sanction·member 동시 갱신).
 */
@Service
class AdminReportReviewService(
    private val memberReportRepository: MemberReportRepository,
    private val memberRepository: MemberRepository,
    private val sanctionRepository: SanctionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    fun review(
        reportId: Long,
        decision: ReviewDecision,
        note: String?,
        admin: AdminPrincipal,
        now: LocalDateTime,
    ) {
        val report = memberReportRepository.findById(reportId).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        // 조건부 UPDATE가 이중 검토를 방어한다 — 0이면 다른 관리자가 먼저 종결한 것.
        val updated = memberReportRepository.completeReview(
            id = reportId,
            result = decision.resultStatus,
            reviewedBy = admin.memberId,
            reviewerName = admin.displayName,
            reviewedAt = now,
            reviewNote = note,
        )
        if (updated == 0) {
            throw WarnException(ErrorCode.REPORT_ALREADY_REVIEWED)
        }

        val level = decision.sanctionLevel ?: return
        applySanction(report, level, note, admin, now)
    }

    /** 제재 적용 — 실패 시 전체 롤백되어 신고 종결도 되돌아간다. */
    private fun applySanction(
        report: MemberReport,
        level: SanctionLevel,
        note: String?,
        admin: AdminPrincipal,
        now: LocalDateTime,
    ) {
        val member = memberRepository.findById(report.reportedMemberId).getOrNull()
            ?: throw WarnException(ErrorCode.NOT_FOUND, "피신고자가 탈퇴하여 제재를 적용할 수 없습니다.")

        val (startsAt, endsAt) = sanctionPeriod(level, now)
        sanctionRepository.save(
            Sanction.impose(
                memberId = member.id,
                origin = SanctionOrigin.REPORTED,
                level = level,
                startsAt = startsAt,
                endsAt = endsAt,
                createdBy = admin.memberId,
                creatorName = admin.displayName,
                memberReportId = report.id,
                note = note,
            ),
        )

        when (level) {
            // 1차(경고)는 계정 상태를 바꾸지 않는다 — 퀴즈 참여만 sanction 구간으로 차단.
            SanctionLevel.WARNING -> return
            SanctionLevel.SUSPENSION -> member.suspendUntil(requireNotNull(endsAt))
            SanctionLevel.PERMANENT_BAN -> member.ban()
        }
        // "즉시 발효": refresh 전량 회수 + 필터의 매 요청 검사로 access token 잔존과 무관하게 차단된다.
        refreshTokenRepository.deleteAllByMemberId(member.id)
    }

    /**
     * 제재 기간 (결정 5).
     * - WARNING: 확정 시점 기준 차주 월요일 00:00부터 7일 — 일요일 23:59:59까지 차단과 동일(endsAt은 exclusive 비교).
     *   확정한 주의 잔여 참여는 허용된다.
     * - SUSPENSION: 확정 즉시부터 14일.
     * - PERMANENT_BAN: 종료 없음.
     */
    private fun sanctionPeriod(level: SanctionLevel, now: LocalDateTime): Pair<LocalDateTime, LocalDateTime?> {
        return when (level) {
            SanctionLevel.WARNING -> {
                val nextMonday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
                nextMonday to nextMonday.plusDays(7)
            }
            SanctionLevel.SUSPENSION -> now to now.plusDays(SUSPENSION_DAYS)
            SanctionLevel.PERMANENT_BAN -> now to null
        }
    }

    companion object {
        // 기획: 2차 제재 = 2주간 서비스 이용 정지
        private const val SUSPENSION_DAYS = 14L
    }
}
