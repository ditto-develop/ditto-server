package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 신고 접수 위치 (FE 진입점).
 *
 * [code]는 FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * 채팅 기능이 출시되면 CHAT_ROOM 값을 추가한다 (값 추가 전용).
 */
enum class MemberReportSource(
    val code: String,
    val description: String,
) {
    PROFILE("profile", "프로필 화면"),
    MATCH_RESULT("match-result", "매칭 결과 화면"),
    ;

    companion object {
        fun from(code: String): MemberReportSource =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
