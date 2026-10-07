package com.ditto.api.admin.qa.dto

import com.ditto.api.userreport.dto.CreateUserReportRequest

/** 더미로 신고하기 폼. 목록에 없는 회원은 typedMemberId 로 직접 넣는다. */
class QaReportForm(
    val dummyId: Long? = null,
    val reportedMemberId: Long? = null,
    val typedMemberId: Long? = null,
    val reasons: List<String> = emptyList(),
    val source: String = "",
    val detail: String? = null,
    val block: Boolean = false,
) {
    val targetMemberId: Long? = typedMemberId ?: reportedMemberId

    fun toRequest(targetId: Long) = CreateUserReportRequest(
        reportedMemberId = targetId,
        reasons = reasons,
        source = source,
        detail = detail?.takeIf { it.isNotBlank() },
        block = block,
    )
}
