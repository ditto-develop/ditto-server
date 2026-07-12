package com.ditto.api.admin.sanction.dto

import java.time.LocalDateTime

/** 회원별 제재 관리 화면 — 요약 + 이력. */
data class MemberSanctionsView(
    val memberId: Long,
    val nickname: String,
    val statusName: String,
    val strikeCount: Long,
    val sanctions: List<SanctionRow>,
)

/** 제재 이력의 한 행 — ACTIVE 건은 직권 해제 가능. */
data class SanctionRow(
    val id: Long,
    val levelDescription: String,
    val originDescription: String,
    val statusDescription: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
    val creatorName: String,
    val note: String?,
    val liftable: Boolean,
)
