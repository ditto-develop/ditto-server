package com.ditto.domain.match

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.withId

object PersonalMatchFixture {

    fun create(
        requesterId: Long = 1L,
        receiverId: Long = 2L,
        quizSetId: Long = 1L,
        status: PersonalMatchStatus = PersonalMatchStatus.PENDING,
        id: Long = 0L,
    ): PersonalMatch = PersonalMatch.create(
        requesterId = requesterId,
        receiverId = receiverId,
        quizSetId = quizSetId,
    ).also { match ->
        if (status != PersonalMatchStatus.PENDING) {
            val field = match::class.java.getDeclaredField("status")
            field.isAccessible = true
            field.set(match, status)
        }
    }.withId(id)
}
