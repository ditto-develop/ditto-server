package com.ditto.api.admin.report

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.report.dto.ReviewDecision
import com.ditto.api.admin.sanction.AdminSanctionService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.entity.SanctionOrigin
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신고 검토 처리 — 신고 종결과 제재 적용을 한 트랜잭션으로 묶는다 (ADR 0009: sanction·member 동시 갱신).
 */
@Service
class AdminReportReviewService(
    private val memberReportRepository: MemberReportRepository,
    private val adminSanctionService: AdminSanctionService,
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
        // 제재 적용 실패(피신고자 탈퇴 등) 시 같은 트랜잭션이라 신고 종결도 롤백된다.
        adminSanctionService.impose(
            memberId = report.reportedMemberId,
            level = level,
            origin = SanctionOrigin.REPORTED,
            admin = admin,
            now = now,
            memberReportId = report.id,
            note = note,
        )
    }
}
