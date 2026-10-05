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
    RECEIVED("검토 대기"),
    ACTIONED("제재함"),
    REJECTED("기각"),
    REJECTED_ABUSIVE("허위 신고로 기각"),
}
