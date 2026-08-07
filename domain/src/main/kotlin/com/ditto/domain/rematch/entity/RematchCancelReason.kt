package com.ditto.domain.rematch.entity

/**
 * 재매칭 쌍이 성사되지 못한 이유. [RematchStatus.CANCELLED]와 함께만 값을 갖는다.
 * 컬럼을 유예했던 배경은 ADR 0012.
 */
enum class RematchCancelReason {
    /** 양쪽 응답이 끝났고 하나 이상이 거절 — 두 선택 값으로도 도출된다. */
    NOT_MUTUAL,

    /** 한쪽이 탈퇴해 성사될 수 없다. 두 선택 값만으로는 구분되지 않아 이 컬럼이 필요해진 사유다. */
    MEMBER_LEFT,
}
