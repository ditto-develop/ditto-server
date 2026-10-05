package com.ditto.domain.sanction.entity

/**
 * 제재 근거. 누적 제재 수에서 FALSE_REPORT(허위 신고자 제재)를 빼기 위한 구분 키.
 */
enum class SanctionOrigin(val description: String) {
    REPORTED("신고 검토 기반"),
    FALSE_REPORT("허위 신고자 제재"),
    MANUAL("어드민 직접 제재"),
}
