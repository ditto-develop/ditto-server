package com.ditto.domain.review.entity

/**
 * 한 회원이 진행하는 평가의 진행 상태. 대상별 응답이 하나라도 확정되면 [IN_PROGRESS],
 * 마지막 대상까지 확정되면 [COMPLETED]로 전이한다.
 *
 * 자식 응답 수를 매번 집계하지 않고 목록 조회에서 바로 읽기 위해 의도적으로 중복 저장한다.
 */
enum class ReviewProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
}
