package com.ditto.api.admin.qa.dto

import com.ditto.api.userreport.dto.CreateUserReportRequest

/** 더미로 신고하기 폼. 직접 넣은 회원 ID 가 목록에서 고른 회원보다 우선한다. 고르지 않은 칸은 빈 값으로 와서 앱 검증이 거부한다. */
class QaReportForm(
    val dummyId: Long? = null,
    val listedMemberId: Long? = null,
    val typedMemberId: Long? = null,
    val reasons: List<String> = emptyList(),
    val source: String = "",
    val detail: String? = null,
    val block: Boolean = false,
) {
    /** 신고할 회원을 고르지 않았으면 만들지 않는다. */
    fun toRequestOrNull(): CreateUserReportRequest? {
        val reportedMemberId = typedMemberId ?: listedMemberId ?: return null
        return CreateUserReportRequest(
            reportedMemberId = reportedMemberId,
            reasons = reasons,
            source = source,
            detail = detail?.takeIf { it.isNotBlank() },
            block = block,
        )
    }
}
