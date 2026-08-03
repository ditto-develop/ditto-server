package com.ditto.api.setting.dto

import java.time.LocalDateTime

/**
 * 차단 목록 항목. 차단 사유는 담지 않는다 — 화면이 사유를 노출하지 않는다(피그마 6.2.2 주석).
 *
 * [id]는 차단된 **회원 ID**다(차단 레코드 ID가 아니다) — 해제 API가 상대 회원을 식별해 지우고,
 * 클라이언트도 회원 단위로 목록을 다룬다.
 */
data class BlockedMemberResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val blockedAt: LocalDateTime,
)
