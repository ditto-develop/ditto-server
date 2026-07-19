package com.ditto.domain.sanction.entity

/**
 * 제재 상태. 전이는 ACTIVE에서만 일어나며 종결 상태는 불변이다.
 *
 * ```
 * ACTIVE → EXPIRED | LIFTED
 * ```
 */
enum class SanctionStatus(val description: String) {
    ACTIVE("적용 중"),
    EXPIRED("기간 만료"),
    LIFTED("어드민 직권 해제"),
}
