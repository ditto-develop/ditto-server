package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 신고 사유.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [requiresDetail]: 접수 시 상세 설명(detail) 입력이 필수인 사유
 * - [isSevere]: 심각 사유(기획의 "즉시 계정 정지" 대상) — 어드민 검토 화면에 즉시 조치 권고 배지로만 표시하고, 최종 수위는 항상 어드민이 확정한다
 * - [guideline]: 어드민 검토 화면에 렌더하는 사유별 대응 안내
 *
 * 값 추가만 허용하며, 이미 배포된 값의 이름/코드 변경·삭제는 금지한다.
 */
enum class MemberReportReason(
    val code: String,
    val description: String,
    val requiresDetail: Boolean = false,
    val isSevere: Boolean = false,
    val guideline: String,
) {
    INAPPROPRIATE_BEHAVIOR(
        "inappropriate-behavior",
        "부적절한 행동 (성희롱·폭언·협박 등)",
        isSevere = true,
        guideline = "즉시 정지 권고 사유. 상세 설명·첨부 이미지와 피신고자의 과거 제재 이력을 함께 확인한다.",
    ),
    MONEY_DEMAND(
        "money-demand",
        "금전 요구 (돈을 요구하거나 상업적 홍보)",
        isSevere = true,
        guideline = "즉시 정지 권고 사유. 사기 정황(반복 요구·외부 링크 유도)이 있으면 영구 차단을 검토한다.",
    ),
    FALSE_INFORMATION(
        "false-information",
        "허위 정보 (프로필 정보가 거짓이거나 사진 도용)",
        isSevere = true,
        guideline = "즉시 정지 권고 사유. 프로필 정보·소개노트를 대조하고 도용 근거(첨부)를 확인한다.",
    ),
    UNDERAGE(
        "underage",
        "미성년자 (19세 미만으로 의심)",
        guideline = "신중 검토 사유. 생년월일 정보를 대조하고 사실로 확인되면 영구 차단한다.",
    ),
    ETC(
        "etc",
        "기타",
        requiresDetail = true,
        guideline = "직접 입력된 상세 설명을 근거로 개별 판단한다.",
    ),
    ;

    companion object {
        fun from(code: String): MemberReportReason =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
