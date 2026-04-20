package com.ditto.domain.match

import com.ditto.domain.match.entity.GroupMatchRoom
import com.ditto.domain.withId

object GroupMatchRoomFixture {

    fun create(
        quizSetId: Long = 1L,
        isActive: Boolean = false,
        participantCount: Int = 0,
        id: Long = 0L,
    ): GroupMatchRoom = GroupMatchRoom.create(quizSetId = quizSetId)
        .also { room ->
            if (participantCount > 0) {
                val countField = room::class.java.getDeclaredField("participantCount")
                countField.isAccessible = true
                countField.set(room, participantCount)
            }
            if (isActive) {
                val isActiveField = room::class.java.getDeclaredField("isActive")
                isActiveField.isAccessible = true
                isActiveField.set(room, true)
            }
        }.withId(id)
}
