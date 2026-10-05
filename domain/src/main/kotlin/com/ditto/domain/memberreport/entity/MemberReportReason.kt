package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 신고 사유.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [requiresDetail]: 접수 시 상세 설명(detail) 입력이 필수인 사유
 * - [isSevere]: 심각 사유(기획의 "즉시 계정 정지" 대상). 어드민 검토 화면에 배지로만 보이고 최종 제재 종류는 어드민이 정한다
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
        guideline = "심각 사유입니다. 상세 설명과 첨부 이미지, 피신고자의 지난 제재 이력을 함께 확인하세요.",
    ),
    MONEY_DEMAND(
        "money-demand",
        "금전 요구 (돈 요구·상업적 홍보)",
        isSevere = true,
        guideline = "심각 사유입니다. 돈을 거듭 요구하거나 외부 링크로 유도하는 등 사기 정황이 있으면 영구 차단을 검토하세요.",
    ),
    FALSE_INFORMATION(
        "false-information",
        "허위 정보 (거짓 프로필·사진 도용)",
        isSevere = true,
        guideline = "심각 사유입니다. 프로필 정보와 소개노트를 살펴보고, 첨부에서 도용 근거를 확인하세요.",
    ),
    UNDERAGE(
        "underage",
        "미성년자 (19세 미만으로 의심)",
        guideline = "신중하게 판단할 사유입니다. 생년월일 정보를 확인하고 미성년자가 맞으면 영구 차단하세요.",
    ),
    ETC(
        "etc",
        "기타",
        requiresDetail = true,
        guideline = "신고자가 쓴 상세 설명을 보고 판단하세요.",
    ),
    ;

    companion object {
        fun from(code: String): MemberReportReason =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
