package com.ditto.api.admin.report.dto

import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.sanction.entity.SanctionLevel

/**
 * 어드민 검토 결정. [sanctionLevel]이 있는 결정은 신고 종결과 함께 제재를 적용한다.
 */
enum class ReviewDecision(
    val description: String,
    val resultStatus: MemberReportStatus,
    val sanctionLevel: SanctionLevel? = null,
) {
    REJECT("기각", MemberReportStatus.REJECTED),
    REJECT_ABUSIVE("악의적 신고로 기각", MemberReportStatus.REJECTED_ABUSIVE),
    WARNING("경고 — 다음 주 퀴즈 참여 불가", MemberReportStatus.ACTIONED, SanctionLevel.WARNING),
    SUSPENSION("2주 이용 정지", MemberReportStatus.ACTIONED, SanctionLevel.SUSPENSION),
    PERMANENT_BAN("영구 차단", MemberReportStatus.ACTIONED, SanctionLevel.PERMANENT_BAN),
}
