package com.ditto.domain.member.repository.querydsl

/** 사유 코드는 FE 탈퇴 화면이 정한 문자열이다. */
data class LeaveReasonCount(
    val leaveReason: String?,
    val count: Long,
)
