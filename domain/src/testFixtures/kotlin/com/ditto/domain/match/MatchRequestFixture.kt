package com.ditto.domain.match

import com.ditto.domain.match.entity.MatchRequest
import com.ditto.domain.match.entity.MatchRequestStatus
import com.ditto.domain.withId

object MatchRequestFixture {

    fun create(
        fromMemberId: Long = 1L,
        toMemberId: Long = 2L,
        quizSetId: Long = 1L,
        status: MatchRequestStatus = MatchRequestStatus.PENDING,
        id: Long = 0L,
    ): MatchRequest = MatchRequest.create(
        fromMemberId = fromMemberId,
        toMemberId = toMemberId,
        quizSetId = quizSetId,
    ).also { request ->
        if (status != MatchRequestStatus.PENDING) {
            val field = request::class.java.getDeclaredField("status")
            field.isAccessible = true
            field.set(request, status)
        }
    }.withId(id)
}
