package com.ditto.api.admin.qa.dto

import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.memberreport.entity.MemberReportStatus

/** 더미로 신고하기 카드. 대상은 더미가 들어 있던 방의 실회원을 앞에, 다른 더미를 뒤에 둔다. */
class QaReportSection(
    val dummies: List<QaMember>,
    val targets: List<QaReportTarget>,
    val reports: List<QaDummyReport>,
    val recentLimit: Int,
) {
    val reasons: List<MemberReportReason> = MemberReportReason.entries

    val sources: List<MemberReportSource> = MemberReportSource.entries
}

class QaReportTarget(
    val member: QaMember,
    val isDummy: Boolean,
)

class QaDummyReport(
    val reportId: Long,
    val reporter: QaMember,
    val reported: QaMember,
    val reasonDescriptions: List<String>,
    val status: MemberReportStatus,
) {
    val isWaiting: Boolean = status == MemberReportStatus.RECEIVED
}
