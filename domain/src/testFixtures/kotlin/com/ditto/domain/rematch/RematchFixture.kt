package com.ditto.domain.rematch

import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.withId
import java.time.LocalDate

object RematchFixture {

    val MONDAY: LocalDate = LocalDate.of(2026, 7, 20)

    fun create(
        sourceGroupMatchId: Long = 1L,
        sourceChatRoomId: Long = 1L,
        quizSetId: Long = 1L,
        weekStartedOn: LocalDate = MONDAY,
        memberIdA: Long = 1L,
        memberIdB: Long = 2L,
        id: Long = 0L,
    ): Rematch = Rematch.create(
        sourceGroupMatchId = sourceGroupMatchId,
        sourceChatRoomId = sourceChatRoomId,
        quizSetId = quizSetId,
        weekStartedOn = weekStartedOn,
        memberIdA = memberIdA,
        memberIdB = memberIdB,
    ).withId(id)
}
