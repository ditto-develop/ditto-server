package com.ditto.api.user.dto

import jakarta.validation.constraints.Size

/**
 * 탈퇴 요청. 탈퇴 화면(피그마 6.2.4) 2단계에서 사유를 고르지 않으면 제출 버튼이 비활성이므로
 * 클라이언트는 항상 [reason]을 보낸다. 다만 사유 없는 탈퇴를 서버가 막을 이유는 없어 optional로 둔다.
 */
data class LeaveRequest(
    @field:Size(max = 50)
    val reason: String? = null,

    // 사유 "기타" 선택 시의 자유 서술(피그마 6.2.4 textarea). code 인 reason 과 분리해 받는다.
    // 기타가 아니어도 값이 오면 저장한다 — 화면 정책 변경에 서버 계약이 흔들리지 않게 강제하지 않는다.
    @field:Size(max = 100)
    val reasonDetail: String? = null,
)
