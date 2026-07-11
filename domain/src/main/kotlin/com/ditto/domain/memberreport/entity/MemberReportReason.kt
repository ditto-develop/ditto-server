package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 신고 사유.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [requiresDetail]: 접수 시 상세 설명(detail) 입력이 필수인 사유
 *
 * 값 추가만 허용하며, 이미 배포된 값의 이름/코드 변경·삭제는 금지한다.
 */
enum class MemberReportReason(
    val code: String,
    val description: String,
    val requiresDetail: Boolean = false,
) {
    INAPPROPRIATE_BEHAVIOR("inappropriate-behavior", "부적절한 행동 (성희롱·폭언·협박 등)"),
    MONEY_DEMAND("money-demand", "금전 요구 (돈을 요구하거나 상업적 홍보)"),
    FALSE_INFORMATION("false-information", "허위 정보 (프로필 정보가 거짓이거나 사진 도용)"),
    UNDERAGE("underage", "미성년자 (19세 미만으로 의심)"),
    ETC("etc", "기타", requiresDetail = true),
    ;

    companion object {
        fun from(code: String): MemberReportReason =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
