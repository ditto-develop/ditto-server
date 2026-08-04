package com.ditto.domain.chat.repository.querydsl

import java.time.LocalDateTime

/**
 * 엔티티가 아니라 **ID만** 돌려준다. 상태를 바꾸는 쪽은 이 ID로 다시 잠금 조회해야 하기 때문이다 —
 * 여기서 엔티티를 통째로 주면 잠금 없이 읽은 스냅샷을 그대로 변경하게 되고,
 * 그 사이 사용자가 커밋한 종료를 전 컬럼 UPDATE 가 덮어쓴다.
 */
interface ChatRoomRepositoryCustom {

    /**
     * [at] 시점에 기한이 지났는데 아직 끝나지 않은 방 — 만료 마감 후보.
     * 열리지 못한 채 기한이 지난 예약 방도 포함한다.
     */
    fun findAllIdsDueToEnd(at: LocalDateTime): List<Long>

    /** [at] 시점에 개방 시각이 됐는데 아직 열리지 않은 방 — 개방 후보. */
    fun findAllIdsDueToOpen(at: LocalDateTime): List<Long>
}
