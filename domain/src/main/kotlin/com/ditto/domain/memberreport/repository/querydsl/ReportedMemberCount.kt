package com.ditto.domain.memberreport.repository.querydsl

data class ReportedMemberCount(
    val memberId: Long,
    val nickname: String,
    val count: Long,
)
