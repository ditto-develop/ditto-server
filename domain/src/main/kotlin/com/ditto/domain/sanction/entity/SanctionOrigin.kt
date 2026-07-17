package com.ditto.domain.sanction.entity

/**
 * 제재 발생 경위. 피신고 차수 산정에서 FALSE_REPORT(허위 신고자 제재)를 제외하기 위한 구분 키.
 */
enum class SanctionOrigin(val description: String) {
    REPORTED("신고 검토 기반"),
    FALSE_REPORT("허위 신고자 제재"),
    MANUAL("어드민 직권"),
}
