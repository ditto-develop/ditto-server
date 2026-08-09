package com.ditto.domain.rematch.repository.querydsl

import com.ditto.domain.rematch.entity.Rematch

interface RematchRepositoryCustom {

    /**
     * 성사됐는데 채팅방이 아직 없는 재매칭 — 방 예약 대상.
     *
     * 성사는 평가 제출 트랜잭션이 확정하고([Rematch.submitWants]) 방은 만들지 않는다. 그 트랜잭션에
     * 방 생성을 묶으면 방 생성 실패가 평가 제출을 되돌리기 때문이다. 그래서 "성사됐는데 방이 없다"는
     * 상태가 정상적으로 존재하고, 이 조회가 그것을 찾아 맞춘다.
     *
     * **같은 `rematch` 를 두 번 처리하는 것만 이 조회가 막는다.** 방이 생기면 다음 주기에 걸리지 않으므로
     * 별도 처리 표시가 필요 없다 — 방 자체가 처리 완료 기록이다. 실패해도 다음 주기에 다시 잡힌다.
     * 쌍 단위 중복(같은 두 사람의 방이 둘)은 허용하므로 여기서 보지 않는다(ADR 0017).
     *
     * 성사 시각 하한은 두지 않는다. 얼마나 오래 밀렸든 방은 열려야 하고, 하한을 박으면 그보다 오래
     * 밀린 성사가 조용히 사라진다(같은 이유로 평가 누락 복구도 하한을 두지 않는다). 대신 [limit]으로
     * 한 번에 처리할 양을 끊는다.
     *
     * ID 가 아니라 엔티티를 돌려준다 — 방을 만들려면 참여자·퀴즈셋·주차가 모두 필요하고, 이 경로는
     * `rematch` 행을 수정하지 않아 잠금 없는 스냅샷이 커밋을 덮을 위험이 없다(수정하는 제출 경로는
     * 행을 잠근다 — ADR 0011).
     */
    fun findMatchedWithoutChatRoom(limit: Int): List<Rematch>
}
