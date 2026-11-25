package com.ditto.domain.chat

import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomStatus
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

    /**
     * #399 이전 규칙으로 예약된 1:1 방 — 목요일에 수락돼 금요일 개방을 기다리는 `SCHEDULED` 방.
     * 지금은 1:1 방이 수락 즉시 열려 팩토리로 만들 수 없지만, 배포 전에 만들어진 행이 운영에 남는다.
     */
    fun scheduledPersonal(
        sourceId: Long = 1L,
        id: Long = 0L,
        now: LocalDateTime = DEFAULT_NOW,
        period: ChatPeriod = ChatPeriod.weekendOf(now),
    ): ChatRoom = ChatRoom.personal(sourceId, period, period.opensAt).withId(id).apply {
        setField("opensAt", period.opensAt)
        setField("status", ChatRoomStatus.SCHEDULED)
    }

    private fun ChatRoom.setField(name: String, value: Any) {
        ChatRoom::class.java.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    fun group(
        sourceId: Long = 1L,
        id: Long = 0L,
        now: LocalDateTime = DEFAULT_NOW,
        period: ChatPeriod = ChatPeriod.weekendOf(now),
    ): ChatRoom = ChatRoom.group(sourceId, period, now).withId(id)

    /** 받은 평가 집계는 끝난 방의 평가만 센다. 평가를 걸어 둘 방이 필요할 때 쓴다. */
    fun endedGroup(sourceId: Long = 1L): ChatRoom =
        group(sourceId).apply { expire(DEFAULT_NOW.plusDays(3)) }

    /** [sourceId]는 `rematch.id`다. */
    fun rematch(
        sourceId: Long = 1L,
        id: Long = 0L,
        now: LocalDateTime = DEFAULT_NOW,
        period: ChatPeriod = ChatPeriod.weekendOf(now),
    ): ChatRoom = ChatRoom.rematch(sourceId, period, now).withId(id)
}
