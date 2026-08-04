package com.ditto.api.user.dto

import jakarta.validation.constraints.Size

/**
 * 탈퇴 요청. 탈퇴 화면(피그마 6.2.4) 2단계에서 사유를 고르지 않으면 제출 버튼이 비활성이므로
 * 클라이언트는 항상 [reason]을 보낸다. 다만 사유 없는 탈퇴를 서버가 막을 이유는 없어 optional로 둔다.
 */
data class LeaveRequest(
    @field:Size(max = 50)
    val reason: String? = null,
)
