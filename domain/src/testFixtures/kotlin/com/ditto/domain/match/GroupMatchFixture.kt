package com.ditto.domain.match

import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.withId

object GroupMatchFixture {

    fun create(
        quizSetId: Long = 1L,
        isActive: Boolean = false,
        participantCount: Int = 0,
        id: Long = 0L,
    ): GroupMatch = GroupMatch.create(quizSetId = quizSetId)
        .also { match ->
            if (participantCount > 0) {
                val countField = match::class.java.getDeclaredField("participantCount")
                countField.isAccessible = true
                countField.set(match, participantCount)
            }
            if (isActive) {
                val isActiveField = match::class.java.getDeclaredField("isActive")
                isActiveField.isAccessible = true
                isActiveField.set(match, true)
            }
        }.withId(id)
}
