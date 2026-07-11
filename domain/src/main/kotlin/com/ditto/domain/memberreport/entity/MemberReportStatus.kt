package com.ditto.domain.memberreport.entity

/**
 * 회원 신고 처리 상태.
 *
 * 전이는 어드민 검토에서만, RECEIVED에서만 일어난다 (검토는 신고당 1회 — 종결 상태는 불변).
 *
 * ```
 * RECEIVED → ACTIONED | REJECTED | REJECTED_ABUSIVE
 * ```
 */
enum class MemberReportStatus(val description: String) {
    RECEIVED("접수됨 (검토 대기)"),
    ACTIONED("검토 후 제재 적용"),
    REJECTED("검토 후 기각"),
    REJECTED_ABUSIVE("악의적 신고로 기각 (신고자 제재 근거)"),
}
