package com.ditto.api.match.dto

import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import java.time.LocalDateTime

data class PersonalMatchResponse(
    val id: Long,
    val quizSetId: Long,
    val requesterId: Long,
    val receiverId: Long,
    val status: PersonalMatchStatus,
    val createdAt: LocalDateTime,
    val respondedAt: LocalDateTime?,
) {
    companion object {
        fun from(match: PersonalMatch): PersonalMatchResponse = PersonalMatchResponse(
            id = match.id,
            quizSetId = match.quizSetId,
            requesterId = match.requesterId,
            receiverId = match.receiverId(),
            status = match.status,
            createdAt = match.createdAt,
            respondedAt = match.respondedAt,
        )
    }
}
