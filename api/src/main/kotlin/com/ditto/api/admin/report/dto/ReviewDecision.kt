package com.ditto.api.admin.report.dto

import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.sanction.entity.SanctionLevel

/**
 * 어드민 검토 결정. [sanctionLevel]이 있는 결정은 신고 종결과 함께 제재를 적용한다.
 */
enum class ReviewDecision(
    val resultStatus: MemberReportStatus,
    val sanctionLevel: SanctionLevel? = null,
) {
    REJECT(MemberReportStatus.REJECTED),
    REJECT_ABUSIVE(MemberReportStatus.REJECTED_ABUSIVE),
    WARNING(MemberReportStatus.ACTIONED, SanctionLevel.WARNING),
    SUSPENSION(MemberReportStatus.ACTIONED, SanctionLevel.SUSPENSION),
    PERMANENT_BAN(MemberReportStatus.ACTIONED, SanctionLevel.PERMANENT_BAN),
    ;

    // 제재 결정은 제재 종류 이름을, 기각은 신고 상태 이름을 그대로 써서 두 화면의 문구가 갈라지지 않게 한다.
    val description: String get() = sanctionLevel?.description ?: resultStatus.description
}
