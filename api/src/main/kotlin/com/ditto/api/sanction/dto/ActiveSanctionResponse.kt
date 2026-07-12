package com.ditto.api.sanction.dto

import java.time.LocalDateTime

/**
 * 피제재자 본인에게 노출하는 제재 정보 — 사유 카테고리·기간만.
 * 신고자·신고 시점·상세 내용은 보복 위험 때문에 절대 노출하지 않는다.
 */
data class ActiveSanctionResponse(
    val level: String,
    val levelDescription: String,
    val reason: String?,
    val reasonDescription: String?,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime?,
)
