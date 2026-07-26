package com.ditto.domain.rematch.entity

enum class RematchStatus {
    /** 한쪽 이상 미응답 — 성사 대기 */
    WAITING,

    /** 양쪽 모두 선택 — 상호 성사 */
    MATCHED,

    /** 성사 불가 확정 */
    CANCELLED,
}
