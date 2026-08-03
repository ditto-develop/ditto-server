package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.withId
import java.time.LocalDateTime

object ChatRoomFixture {

    /** 개방된 주말 한가운데(금요일 정오)를 기본 시각으로 둬서 만들자마자 ACTIVE 인 방을 준다. */
    val DEFAULT_NOW: LocalDateTime = LocalDateTime.of(2026, 3, 13, 12, 0)

    fun personal(
        sourceId: Long = 1L,
        id: Long = 0L,
        now: LocalDateTime = DEFAULT_NOW,
        period: ChatPeriod = ChatPeriod.weekendOf(now),
    ): ChatRoom = ChatRoom.personal(sourceId, period, now).withId(id)

    fun group(
        sourceId: Long = 1L,
        id: Long = 0L,
        now: LocalDateTime = DEFAULT_NOW,
        period: ChatPeriod = ChatPeriod.weekendOf(now),
    ): ChatRoom = ChatRoom.group(sourceId, period, now).withId(id)
}
