package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatRoom
import java.time.LocalDateTime

interface ChatRoomRepositoryCustom {

    /**
     * [at] 시점에 기한이 지났는데 아직 끝나지 않은 방 — 만료 마감 대상.
     * 열리지 못한 채 기한이 지난 예약 방도 포함한다.
     */
    fun findAllDueToEnd(at: LocalDateTime): List<ChatRoom>

    /** [at] 시점에 개방 시각이 됐는데 아직 열리지 않은 방 — 개방 대상. */
    fun findAllDueToOpen(at: LocalDateTime): List<ChatRoom>
}
