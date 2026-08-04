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

    /**
     * 끝났는데 평가가 아직 열리지 않은 채팅방 ID — 누락 복구(anti-join) 대상.
     *
     * 채팅 종료와 평가 생성을 한 트랜잭션으로 묶지 않기로 했으므로(계획서 ⑤-1), 종료는 커밋됐는데
     * 평가 생성이 실패한 방이 남을 수 있다. 아무도 재시도하지 않으면 그 참여자들은 영영 평가를 못 한다.
     *
     * **종료 시각 하한을 두지 않는다.** "이 기능 이전에 끝난 방"은 도입 시점에 한 번 생기는 문제라
     * 일회성 데이터 정리로 다뤄야 한다. 쿼리에 하한을 박으면 그 필터가 영구히 남아, 하한보다 오래
     * 밀린 방이 복구 대상에서 조용히 사라진다 — 복구 장치가 유실을 만드는 셈이다.
     *
     * 대신 [limit]으로 한 번에 처리할 양을 끊는다 — 장애 후 밀린 방이 많을 때 한 호출이 전부 떠안지 않게 한다.
     */
    fun findEndedChatRoomIdsWithoutReview(limit: Int): List<Long>
}
