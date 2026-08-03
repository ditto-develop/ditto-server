package com.ditto.domain.review.repository.querydsl

import com.ditto.domain.review.entity.MemberReview

interface MemberReviewRepositoryCustom {

    /**
     * 작성자의 아직 완료되지 않은 평가를, 평가 가능해진 순서(오래된 순)로 조회한다.
     *
     * 같은 주의 채팅들이 같은 시각에 닫히면 `availableAt`이 겹칠 수 있어 `id`로 순서를 고정한다 —
     * 정렬 키가 하나면 동순위 행의 순서를 DB가 보장하지 않아 목록이 요청마다 뒤바뀐다.
     */
    fun findPendingByAuthorOldestFirst(authorMemberId: Long): List<MemberReview>
}
